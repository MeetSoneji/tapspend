package com.meetsoneji.tapspend

import com.meetsoneji.tapspend.core.capture.Classifier
import com.meetsoneji.tapspend.core.capture.Direction
import com.meetsoneji.tapspend.core.capture.Hint
import com.meetsoneji.tapspend.core.capture.Instrument
import com.meetsoneji.tapspend.core.capture.LearnedRule
import com.meetsoneji.tapspend.core.capture.TxnKind
import com.meetsoneji.tapspend.core.capture.TxnParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTest {
    private val me = listOf("Meet", "Soneji")
    private fun classify(text: String, sender: String = "") =
        TxnParser.parse(text, sender)?.let { Classifier.classify(it, me, emptyMap()) }

    // ---------- spends ----------
    @Test fun hdfcCardSwipe() {
        val p = TxnParser.parse("Txn Rs.450.00 On HDFC Bank Card 5678 At SWIGGY On 2026-11-17:20:41:12 Not You? Call 18002586161/SMS BLOCK CC 5678 to 7308080808", "VM-HDFCBK")!!
        assertEquals(Direction.DEBIT, p.direction); assertEquals(45_000L, p.amountMinor)
        assertEquals(Instrument.CARD, p.instrument); assertEquals("5678", p.last4); assertEquals("HDFC", p.institution)
        assertEquals("Swiggy", p.merchant)
        val c = Classifier.classify(p, me, emptyMap()); assertEquals(TxnKind.EXPENSE, c.kind); assertEquals("preset-food", c.categoryKey)
    }

    @Test fun hdfcOldFormatCard() {
        val p = TxnParser.parse("Thank you for using your HDFC Bank Credit Card ending 5678 for Rs 1,250.50 at DMART AVENUE on 17-11-2026 20:41:12. Authorization code:- 123456")!!
        assertEquals(125_050L, p.amountMinor); assertEquals("5678", p.last4); assertEquals("Dmart Avenue", p.merchant)
        assertEquals("preset-groceries", Classifier.classify(p, me, emptyMap()).categoryKey)
    }

    @Test fun hdfcUpiFromAccount() {
        val p = TxnParser.parse("Sent Rs.120.00 From HDFC Bank A/C *1234 To Chai Point On 17/11/26 Ref 432112345678 Not You? Call 18002586161/SMS BLOCK UPI to 7308080808")!!
        assertEquals(Instrument.ACCOUNT, p.instrument); assertEquals("1234", p.last4); assertEquals("Chai Point", p.merchant)
        assertEquals("432112345678", p.ref); assertEquals("preset-chai", Classifier.classify(p, me, emptyMap()).categoryKey)
    }

    @Test fun upiToVpaUsesHandleAsName() {
        val p = TxnParser.parse("Money Sent! Rs 86.00 from HDFC Bank A/c **1234 to rapido@ybl on 17-11-26 UPI Ref 432112345679")!!
        assertEquals("Rapido", p.merchant); assertEquals("preset-transport", Classifier.classify(p, me, emptyMap()).categoryKey)
    }

    @Test fun iciciCard() {
        val p = TxnParser.parse("INR 2,199.00 spent using ICICI Bank Card XX9012 on 16-Nov-26 on AMAZON PAY IN. Avl Limit: INR 1,23,456.00. If not you, call 1800 2662/SMS BLOCK 9012 to 9215676766.")!!
        assertEquals(219_900L, p.amountMinor); assertEquals("9012", p.last4); assertEquals("ICICI", p.institution)
        assertEquals("Amazon Pay", p.merchant); assertEquals("preset-shopping", Classifier.classify(p, me, emptyMap()).categoryKey)
    }

    @Test fun iciciAccountUpi() {
        val p = TxnParser.parse("ICICI Bank Acct XX345 debited for Rs 612.00 on 16-Nov-26; BLINKIT credited. UPI:432198765432. Call 18002662 for dispute. SMS BLOCK 345 to 9215676766.")!!
        assertEquals(Direction.DEBIT, p.direction); assertEquals("345", p.last4); assertEquals("Blinkit", p.merchant)
        assertEquals("432198765432", p.ref)
    }

    @Test fun sbiUpiWithoutCurrencySymbol() {
        val p = TxnParser.parse("Dear UPI user A/C X4321 debited by 40.0 on date 17Nov26 trf to CHAI POINT Refno 432112345111. If not u? call 1800111109. -SBI")!!
        assertEquals(4_000L, p.amountMinor); assertEquals("4321", p.last4); assertEquals("Chai Point", p.merchant); assertEquals("SBI", p.institution)
    }

    @Test fun sbiCardAndAxisCard() {
        val s = TxnParser.parse("Rs.312.00 spent on your SBI Credit Card ending 1102 at UBER INDIA on 16/11/26. Trxn. not done by you? Report at https://sbicard.com/Dispute")!!
        assertEquals("1102", s.last4); assertEquals("preset-transport", Classifier.classify(s, me, emptyMap()).categoryKey)
        val a = TxnParser.parse("Spent INR 999.00 Axis Bank Card no. XX4410 16-11-26 21:05:11 IST NETFLIX Avl Limit: INR 98,765.43 Not you? SMS BLOCK 4410 to 919951860002")!!
        assertEquals("4410", a.last4); assertEquals("Netflix", a.merchant)
        assertEquals("Uber", s.merchant); assertEquals("preset-entertainment", Classifier.classify(a, me, emptyMap()).categoryKey)
    }

    @Test fun kotakUpi() {
        val p = TxnParser.parse("Sent Rs.250.00 from Kotak Bank AC X7788 to zomato@icici on 17-11-26.UPI Ref 432111122233. Not you, https://kotak.com/KBANKT/Fraud")!!
        assertEquals("Zomato", p.merchant); assertEquals("7788", p.last4); assertEquals("Kotak", p.institution)
    }

    @Test fun paymentAppNotifications() {
        val g = TxnParser.parse("Paid ₹450 to Swiggy. Paid with HDFC Bank ••1234")!!
        assertEquals(45_000L, g.amountMinor); assertEquals("Swiggy", g.merchant); assertEquals("1234", g.last4)
        val pp = TxnParser.parse("₹86 paid to Rapido")!!
        assertEquals("Rapido", pp.merchant); assertEquals(Direction.DEBIT, pp.direction)
    }

    // ---------- not spending ----------
    @Test fun creditCardBillPaymentIsATransferOnBothSides() {
        assertEquals(TxnKind.TRANSFER, classify("Sent Rs.25840.00 From HDFC Bank A/C *1234 To CRED Club On 08/12/26 Ref 432155555555")!!.kind)
        val received = TxnParser.parse("Payment of Rs 25,840.00 has been received on your HDFC Bank Credit Card ending 5678 on 08-12-2026. Thank you.")!!
        assertEquals(Hint.CARD_PAYMENT_RECEIVED, received.hint)
        assertEquals(TxnKind.TRANSFER, Classifier.classify(received, me, emptyMap()).kind)
    }

    @Test fun selfTransferByOwnName() {
        assertEquals(TxnKind.TRANSFER, classify("Sent Rs.5000.00 From HDFC Bank A/C *1234 To MEET SONEJI On 17/11/26 Ref 432177777777")!!.kind)
        // first name alone is not enough
        assertEquals(TxnKind.EXPENSE, classify("Sent Rs.500.00 From HDFC Bank A/C *1234 To MEET TEA STALL On 17/11/26 Ref 432177777778")!!.kind)
    }

    @Test fun investmentsAtmAndWallet() {
        assertEquals(TxnKind.TRANSFER, classify("Sent Rs.5000.00 From HDFC Bank A/C *1234 To Zerodha Broking On 17/11/26 Ref 432177777779")!!.kind)
        assertEquals(TxnKind.TRANSFER, classify("Rs.2000.00 withdrawn at ATM from HDFC Bank A/c XX1234 on 17-11-26")!!.kind)
    }

    @Test fun refundsCashbackAndIncome() {
        val r = TxnParser.parse("Refund of Rs 2,199.00 from AMAZON has been credited to your ICICI Bank Credit Card XX9012 on 20-Nov-26.")!!
        assertEquals(Direction.CREDIT, r.direction); assertEquals(Hint.REFUND, r.hint); assertEquals("Amazon", r.merchant)
        assertEquals(TxnKind.REFUND, Classifier.classify(r, me, emptyMap()).kind)
        assertEquals(TxnKind.REFUND, classify("Dear Customer, Rs.450.00 has been reversed to your HDFC Bank Card 5678 for transaction at SWIGGY")!!.kind)
        assertEquals(TxnKind.CASHBACK, classify("Cashback of Rs.150.00 has been credited to your SBI Card ending 1102.")!!.kind)
        assertEquals(TxnKind.INCOME, classify("Rs.5000.00 credited to HDFC Bank A/c XX1234 on 17-11-26 by A/c linked to VPA meet@okaxis (UPI Ref No 432177777777).")!!.kind)
    }

    @Test fun ignoresOtpDeclinesOffersAndReminders() {
        assertNull(TxnParser.parse("123456 is the OTP for txn of INR 450.00 at SWIGGY on HDFC Bank card ending 5678. Valid till 20:45. Do not share OTP"))
        assertNull(TxnParser.parse("Txn of Rs 450 on card 5678 at SWIGGY declined due to insufficient limit"))
        assertNull(TxnParser.parse("Get a pre-approved Personal Loan of Rs 5,00,000 at 10.5%. Apply now"))
        assertNull(TxnParser.parse("Your EMI of Rs 4,500 will be debited on 05-12-2026 from A/c XX1234"))
        assertNull(TxnParser.parse("Hi, see you at 5?"))
    }

    @Test fun statementIsRecognisedButNotAnExpense() {
        val s = TxnParser.parse("Your HDFC Bank Credit Card ending 5678 statement has been generated. Total Amt Due: Rs 25,840.00 Min Amt Due: Rs 1,300.00 Due Date: 08-12-2026")!!
        assertEquals(Hint.STATEMENT, s.hint); assertEquals(2_584_000L, s.amountMinor)
        assertEquals(TxnKind.IGNORED, Classifier.classify(s, me, emptyMap()).kind)
    }

    @Test fun learnedRulesWin() {
        val p = TxnParser.parse("Sent Rs.300.00 From HDFC Bank A/C *1234 To Ramesh Kirana On 17/11/26 Ref 432100000001")!!
        val key = TxnParser.merchantKey(p.merchant!!)
        val c = Classifier.classify(p, me, mapOf(key to LearnedRule(TxnKind.EXPENSE, "preset-groceries")))
        assertEquals("preset-groceries", c.categoryKey)
        val t = Classifier.classify(p, me, mapOf(key to LearnedRule(TxnKind.TRANSFER, null)))
        assertEquals(TxnKind.TRANSFER, t.kind)
    }

    @Test fun senderFilterAndMerchantKeys() {
        assertTrue(TxnParser.isBankSender("VM-HDFCBK")); assertTrue(TxnParser.isBankSender("AX-ICICIT-S"))
        assertFalse(TxnParser.isBankSender("+919876543210")); assertFalse(TxnParser.isBankSender("Mom"))
        assertEquals(TxnParser.merchantKey("SWIGGY"), TxnParser.merchantKey("Swiggy Ltd"))
        assertNotNull(Classifier.guessCategory("Swiggy Instamart")); assertEquals("groceries", Classifier.guessCategory("Swiggy Instamart"))
    }
}
