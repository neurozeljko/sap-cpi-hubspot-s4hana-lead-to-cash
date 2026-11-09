package cpi.scripts

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

Message processData(Message message) {
    String dealId = message.getProperty("Prop_Deal_ID")
    String dealName = message.getProperty("Prop_Deal_Name")
    double dealAmount = message.getProperty("Prop_Deal_Amount") as Double
    String currencyCode = message.getProperty("Prop_Currency")
    String customerAccount = message.getProperty("Prop_Customer_Account")
    String idempotencyKey = message.getProperty("Prop_Idempotency_Key")
    String correlationId = message.getProperty("Prop_Correlation_ID") ?: UUID.randomUUID().toString()
    String lineItemsJson = message.getProperty("Prop_Line_Items_JSON")

    def jsonSlurper = new JsonSlurper()
    def lineItems = jsonSlurper.parseText(lineItemsJson)

    def s4SalesOrder = [
        SalesOrderType: "OR",
        SalesOrganization: "1010",
        DistributionChannel: "10",
        OrganizationDivision: "00",
        SoldToParty: customerAccount,
        PurchaseOrderByCustomer: "HS-DEAL-${dealId}".take(35),
        CustomerPurchaseOrderDate: new Date().format("yyyy-MM-dd"),
        TransactionCurrency: currencyCode,
        TotalNetAmount: dealAmount,
        CompleteDeliveryIsDefined: true,
        to_Item: lineItems.collect { item ->
            [
                SalesOrderItem: item.itemPosition ?: "10",
                Material: item.productSku ?: "STANDARD-ENT-SUITE",
                RequestedQuantity: (item.quantity ?: 1.0).toString(),
                RequestedQuantityUnit: item.unitOfMeasure ?: "EA",
                NetPriceAmount: (item.unitPrice ?: 0.0) as Double,
                TransactionCurrency: currencyCode
            ]
        },
        auditTrace: [
            idempotencyLockKey: idempotencyKey,
            mplCorrelationTrackingId: correlationId,
            originatingCrm: "HUBSPOT_ENTERPRISE_API",
            dealReference: dealName,
            contractSignedTimestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'")
        ]
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(s4SalesOrder)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("X-CleanCore-Target", "S4HANA_SALES_ORDER_ODATA_V4")
    message.setHeader("X-Idempotency-Status", "TRANSACTION_COMMITTED")
    return message
}
