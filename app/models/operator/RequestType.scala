package models.operator

import enumeratum.{EnumEntry, PlayEnum}

sealed abstract class RequestType(override val entryName: String) extends EnumEntry

object RequestType extends PlayEnum[RequestType] {

  override val values: IndexedSeq[RequestType] = findValues

  case object Create extends RequestType("CREATE")
  case object Update extends RequestType("UPDATE")
  case object Delete extends RequestType("DELETE")
}
