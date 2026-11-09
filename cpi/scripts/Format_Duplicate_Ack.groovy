package cpi.scripts

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

Message processData(Message message) {
    String dealId = message.getProperty("Prop_Deal_ID")
    String idempotencyKey = message.getProperty("Prop_Idempotency_Key")
    String correlationId = message.getProperty("Prop_Correlation_ID") ?: UUID.randomUUID().toString()

    def duplicateAckPayload = [
        hubSpotIdempotentAcknowledgment: [
            status: "DUPLICATE_WEBHOOK_DISCARDED",
            statusCode: 200,
            dealId: dealId,
            idempotencyKey: idempotencyKey,
            correlationId: correlationId,
            reason: "Contract event already posted to S/4HANA Finance. Duplicate transaction suppressed to prevent double-billing.",
            timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
        ]
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(duplicateAckPayload)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("X-Idempotency-Status", "SUPPRESSED_DUPLICATE")
    return message
}
