package com.shejera.gedcom.mapping

import com.shejera.gedcom.GedcomTags

object FieldMapping {
  val individualFields =
      mapOf(
          "sex" to GedcomTags.SEX,
          "is_living" to null,
      )

  val individualNameFields =
      mapOf(
          "name_full" to GedcomTags.NAME,
          "given_name" to GedcomTags.GIVN,
          "surname" to GedcomTags.SURN,
          "name_type" to GedcomTags.TYPE,
      )

  val familySpouseFields =
      mapOf(
          "role" to null,
          "sort_order" to null,
      )

  val familyChildFields =
      mapOf(
          "pedigree" to GedcomTags.PEDI,
          "sort_order" to null,
      )

  val eventFields =
      mapOf(
          "tag" to null,
          "event_type" to GedcomTags.TYPE,
          "date_text" to GedcomTags.DATE,
          "description" to null,
      )

  val placeFields =
      mapOf(
          "name" to GedcomTags.PLAC,
      )
}
