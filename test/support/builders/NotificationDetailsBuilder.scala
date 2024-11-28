package support.builders

import models.operator.NotificationType.Rpo
import models.operator.responses.NotificationDetails

import java.time.Instant

object NotificationDetailsBuilder {

  val aNotificationDetails: NotificationDetails = NotificationDetails(
    notificationType = Rpo,
    isActiveSeller = None,
    isDueDiligence = None,
    firstPeriod = 2024,
    receivedDateTime = Instant.now()
  )
}
