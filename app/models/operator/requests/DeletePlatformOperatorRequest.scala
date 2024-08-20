package models.operator.requests

final case class DeletePlatformOperatorRequest(
                                                subscriptionId: String,
                                                operatorId: String
                                              )

object DeletePlatformOperatorRequest {

}
