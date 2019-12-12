package io.onfhir.validation

import io.onfhir.api.util.IOUtil
import io.onfhir.config.FhirConfigurationManager
import io.onfhir.r4.config.R4Configurator
import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.io.Source

@RunWith(classOf[JUnitRunner])
class ProfileValidationTest extends Specification {
  //Initialize the environment
  val resourceProfiles = IOUtil.readStandardBundleFile("profiles-resources.json", Set("StructureDefinition")).flatMap(StructureDefinitionParser.parseProfile)
  val dataTypeProfiles = IOUtil.readStandardBundleFile("profiles-types.json", Set("StructureDefinition")).flatMap(StructureDefinitionParser.parseProfile)
  val valueSetsOrCodeSystems =
    IOUtil.readStandardBundleFile("valuesets.json", Set("ValueSet", "CodeSystem")) ++
      IOUtil.readStandardBundleFile("v3-codesystems.json", Set("ValueSet", "CodeSystem"))

  val extraProfiles = Seq(
    IOUtil.readInnerResource("/fhir/r4/profiles/MyObservation.StructureDefinition.json"),
    IOUtil.readInnerResource("/fhir/r4/profiles/MySampledData.StructureDefinition.json"),
  ).flatMap(StructureDefinitionParser.parseProfile)



  FhirConfigurationManager.initialize(new R4Configurator)
  FhirConfigurationManager.fhirConfig.profileRestrictions = (dataTypeProfiles ++ resourceProfiles ++ extraProfiles).map(p => p.url -> p).toMap
  FhirConfigurationManager.fhirConfig.valueSetRestrictions = TerminologyParser.parseValueSetBundle(valueSetsOrCodeSystems)


  sequential
  "ProfileValidation" should {
   "validate a valid FHIR resource against base definitions" in {
      val fhirContentValidator = FhirContentValidator(FhirConfigurationManager.fhirConfig, "http://hl7.org/fhir/StructureDefinition/Observation")
      var observation =  JsonMethods.parse(Source.fromInputStream(getClass.getResourceAsStream("/fhir/r4/valid/observation-glucose.json")).mkString).asInstanceOf[JObject]
      var issues = fhirContentValidator.validateComplexContent(observation)
      issues.isEmpty mustEqual(true)

      observation =  JsonMethods.parse(Source.fromInputStream(getClass.getResourceAsStream("/fhir/r4/valid/observation-erythrocyte.json")).mkString).asInstanceOf[JObject]
      issues = fhirContentValidator.validateComplexContent(observation)
      issues.isEmpty mustEqual(true)

      observation =  JsonMethods.parse(Source.fromInputStream(getClass.getResourceAsStream("/fhir/r4/valid/observation-bp.json")).mkString).asInstanceOf[JObject]
      issues = fhirContentValidator.validateComplexContent(observation)
      issues.isEmpty mustEqual(true)

      observation =  JsonMethods.parse(Source.fromInputStream(getClass.getResourceAsStream("/fhir/r4/valid/observation-group.json")).mkString).asInstanceOf[JObject]
      issues = fhirContentValidator.validateComplexContent(observation)
      issues.isEmpty mustEqual(true)

      observation =  JsonMethods.parse(Source.fromInputStream(getClass.getResourceAsStream("/fhir/r4/valid/observation-reject.json")).mkString).asInstanceOf[JObject]
      issues = fhirContentValidator.validateComplexContent(observation)
      issues.exists(_.severity == "error") mustEqual(false)
    }

    "not validate an invalid FHIR resource against base definitions" in {
      val fhirContentValidator = FhirContentValidator(FhirConfigurationManager.fhirConfig, "http://hl7.org/fhir/StructureDefinition/Observation")
      var observation =  JsonMethods.parse(Source.fromInputStream(getClass.getResourceAsStream("/fhir/r4/invalid/observation-invalid-cardinality.json")).mkString).asInstanceOf[JObject]
      var issues = fhirContentValidator.validateComplexContent(observation)
      issues.isEmpty mustEqual(false)
      issues.exists(i => i.location.head == "identifier") mustEqual(true)  // Should be an array but given as object
      issues.exists(_.location.head == "code.coding") mustEqual(true) //Should an array but given as object
      issues.exists(_.location.head == "subject") mustEqual(true) //Should an array but given as object
      issues.exists(i => i.location.head == "status") mustEqual(true) //Is required but not given

      observation =  JsonMethods.parse(Source.fromInputStream(getClass.getResourceAsStream("/fhir/r4/invalid/observation-invalid-basics.json")).mkString).asInstanceOf[JObject]
      issues = fhirContentValidator.validateComplexContent(observation)
      issues.isEmpty mustEqual(false)
      issues.exists(i => i.location.head == "status") mustEqual(true) //Invalid primitive empty code
      issues.exists(i => i.location.head == "extraElement") mustEqual(true) //Invalid extra element
      issues.exists(i => i.location.head == "category[0].coding[0].display") mustEqual(true) //Invalid primitive empty string
      issues.exists(i => i.location.head == "component[0].valueQuantity.value") mustEqual(true) //Invalid decimal (decimal as string)
      issues.exists(i => i.location.head == "component[0].interpretation[0].coding[0].extraElement") mustEqual(true) //Invalid extra element in inner parts
      issues.exists(i => i.location.head == "extension[0].url") mustEqual(true) //Required but not exist
      issues.exists(i => i.location.head == "extension[0]")  mustEqual(true) //Constraint failure both extension has value and extension

      observation =  JsonMethods.parse(Source.fromInputStream(getClass.getResourceAsStream("/fhir/r4/invalid/observation-invalid-complex.json")).mkString).asInstanceOf[JObject]
      issues = fhirContentValidator.validateComplexContent(observation)
      issues.isEmpty mustEqual(false)
      issues.exists(i => i.location.head == "status" && i.severity == "error") mustEqual true
      issues.exists(i => i.location.head == "category[0]" && i.severity == "warning") mustEqual true
      issues.exists(i => i.location.head == "interpretation[0]" && i.severity == "warning") mustEqual true
      issues.exists(i => i.location.head == "component[0].interpretation[0]" && i.severity == "warning") mustEqual true
    }

    "validate a valid FHIR resource against profile" in {
      var observation =  JsonMethods.parse(Source.fromInputStream(getClass.getResourceAsStream("/fhir/r4/valid/observation-my.json")).mkString).asInstanceOf[JObject]
      val fhirContentValidator = FhirContentValidator(FhirConfigurationManager.fhirConfig, "http://example.org/fhir/StructureDefinition/MyObservation")
      var issues = fhirContentValidator.validateComplexContent(observation)
      issues.isEmpty mustEqual(true)
    }

    "not validate an invalid FHIR resource against profile" in {
      var observation =  JsonMethods.parse(Source.fromInputStream(getClass.getResourceAsStream("/fhir/r4/invalid/observation-my-invalid.json")).mkString).asInstanceOf[JObject]
      val fhirContentValidator = FhirContentValidator(FhirConfigurationManager.fhirConfig, "http://example.org/fhir/StructureDefinition/MyObservation")
      var issues = fhirContentValidator.validateComplexContent(observation)
      issues.isEmpty mustEqual(false)
      issues.exists(i => i.location.head == "identifier[0].system" && i.severity == "error") mustEqual true //Slice indicates system as required
      issues.exists(i => i.location.head == "status" && i.severity == "error") mustEqual true //Fixed value error
      issues.exists(i => i.location.head == "category" && i.severity == "error") mustEqual true //Not array error
      issues.exists(i => i.location.head == "code" && i.severity == "error") mustEqual true //Shpuld not be array error
      issues.exists(i => i.location.head == "subject" && i.severity == "error") mustEqual true //Reference type does not match
      issues.exists(i => i.location.head == "effectiveDateTime" && i.severity == "error") mustEqual true //Invalid date time format
      issues.exists(i => i.location.head == "issued" && i.severity == "error") mustEqual true //Invalid instant format
      issues.exists(i => i.location.head == "note[0].authorString" && i.severity == "error") mustEqual true //Exceed max length
      issues.exists(i => i.location.head == "note[0].text" && i.severity == "error") //Required field is missing
      issues.exists(i => i.location.head == "component[0].valueSampledData.factor" && i.severity == "error") mustEqual true  //Fixed value error
      issues.exists(i => i.location.head == "component[0].valueSampledData.upperLimit" && i.severity == "error") mustEqual true //Invalid decimal (given as string)
      issues.exists(i => i.location.head == "component[0].valueSampledData.lowerLimit" && i.severity == "error") mustEqual true //DataType profile, Missing element
      issues.exists(i => i.location.head == "component[0].valueSampledData.origin" && i.severity == "error") mustEqual true //DataType profile, missing element
      issues.exists(i => i.location.head == "component[6].valueString" && i.severity == "error") mustEqual true  //Slice, wrong data type
      issues.exists(i => i.location.head == "component[7].valueInteger" && i.severity == "error") mustEqual true  //Slice, wrong data type
      issues.exists(i => i.location.head == "component" && i.severity == "error") mustEqual true  //Slice cardinality
      issues.exists(i => i.location.head == "component[1].interpretation" && i.severity == "error") mustEqual true //Slicing match, required field is missing
      issues.exists(i => i.location.head == "component[1].referenceRange" && i.severity == "error") mustEqual true //Slicing match, required field is missing
      issues.exists(i => i.location.head == "basedOn" && i.severity == "error") mustEqual true//Missing element
    }

  }
}