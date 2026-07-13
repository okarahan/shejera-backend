package com.shejera.gedcom.mapping

import com.shejera.gedcom.GedcomTags

object RecordMapping {
  val tableToGedcomRecord =
      mapOf(
          "gedcom_tree" to null,
          "individual" to GedcomTags.INDI,
          "individual_name" to GedcomTags.NAME,
          "family" to GedcomTags.FAM,
          "family_spouse" to null,
          "family_child" to GedcomTags.CHIL,
          "place" to GedcomTags.PLAC,
          "individual_event" to null,
          "family_event" to null,
          "note" to GedcomTags.NOTE,
          "note_link" to null,
      )

  val xrefPrefixByTable =
      mapOf(
          "individual" to "I",
          "family" to "F",
          "note" to "N",
          "source" to "S",
      )
}
