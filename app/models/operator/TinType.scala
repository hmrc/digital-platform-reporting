package models.operator

import enumeratum._

sealed abstract class TinType(override val entryName: String) extends EnumEntry

object TinType extends PlayEnum[TinType] {

  override val values: IndexedSeq[TinType] = findValues

  case object Dpi extends TinType("DPI")
  case object Utr extends TinType("UTR")
  case object Vrn extends TinType("VRN")
  case object Empref extends TinType("EMPREF")
  case object Brocs extends TinType("BROCS")
  case object Chrn extends TinType("CHRN")
  case object Other extends TinType("OTHER")
}


