package cpi.scripts

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

Message processData(Message message) {
    def exception = message.getProperty("CamelExceptionCaught")
    String dealId = message.getProperty("Prop_Deal_ID") ?: "UNKNOWN_DEAL"
    String correlationId = message.getHeader("SAP_MplCorrelationId", String) ?: UUID.randomUUID().toString()
    String errorMsg = exception ? exception.getMessage() : "HubSpot contract settlement execution fault."

    def dlqPayload = [
        hubSpotDeadLetterQueue: [
            incidentTrackingId: correlationId,
            dealId: dealId,
            financialRiskMitigation: "PREVENTED_UNBALANCED_REVENUE_COMMIT",
            failureCause: errorMsg,
            recommendedAction: "REPLAY_FROM_BTP_DATA_STORE_ONCE_S4_READY",
            quarantineTimestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
        ]
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(dlqPayload)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("X-HubSpot-State", "QUARANTINED_IN_DLQ")
    return message
}
