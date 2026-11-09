package cpi.scripts

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.security.MessageDigest

Message processData(Message message) {
    def reader = message.getBody(java.io.Reader)
    def jsonSlurper = new JsonSlurper()
    def payload = jsonSlurper.parse(reader)

    String dealId = payload.dealId ?: "DEAL-UNKNOWN-00"
    String dealName = payload.dealName ?: "Enterprise Expansion"
    double dealAmount = (payload.amount ?: 0.0) as Double
    String currencyCode = payload.currency ?: "USD"
    String customerAccount = payload.customerAccountNumber ?: "CUST-DEFAULT-100"
    def lineItems = payload.lineItems ?: []
    boolean isRetrySimulation = payload.isSimulatedDuplicate ?: false

    if (lineItems.isEmpty()) {
        throw new IllegalArgumentException("HUBSPOT_PAYLOAD_INVALID: Deal line items list cannot be empty.")
    }

    String rawPayloadString = JsonOutput.toJson(payload)
    MessageDigest digest = MessageDigest.getInstance("SHA-256")
    byte[] hashBytes = digest.digest(rawPayloadString.getBytes("UTF-8"))
    StringBuilder hexString = new StringBuilder()
    for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b)
        if (hex.length() == 1) hexString.append('0')
        hexString.append(hex)
    }
    String payloadHash = hexString.toString()
    String idempotencyKey = "IDEMP_${dealId}_${payloadHash.take(16)}"

    String attemptHeader = message.getProperty("Prop_Delivery_Attempt") ?: "1"
    boolean isDuplicate = isRetrySimulation || (attemptHeader.toInteger() > 1 && payload.forceDuplicateCheck == true)

    message.setProperty("Prop_Deal_ID", dealId)
    message.setProperty("Prop_Deal_Name", dealName)
    message.setProperty("Prop_Deal_Amount", dealAmount)
    message.setProperty("Prop_Currency", currencyCode)
    message.setProperty("Prop_Customer_Account", customerAccount)
    message.setProperty("Prop_Payload_Hash", payloadHash)
    message.setProperty("Prop_Idempotency_Key", idempotencyKey)
    message.setProperty("Prop_Is_Duplicate", isDuplicate.toString())
    message.setProperty("Prop_Line_Items_JSON", JsonOutput.toJson(lineItems))

    return message
}
