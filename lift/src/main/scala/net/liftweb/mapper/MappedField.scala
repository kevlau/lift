package net.liftweb.mapper

/*                                                *\
 (c) 2006-2007 WorldWide Conferencing, LLC
 Distributed under an Apache License
 http://www.apache.org/licenses/LICENSE-2.0
 \*                                                 */

import scala.collection.mutable._
import java.lang.reflect.Method
import java.sql.{ResultSet, Types}
import scala.xml.{Elem, Text, Node, NodeSeq}
import java.util.Date
import net.liftweb.http.S

trait BaseMappedField {
  def getJDBCFriendly(field : String) : Object
  
  def getJDBCFriendly: Object

  /**
   * Get the JDBC SQL Type for this field
   */
  def getTargetSQLType(field : String): int

  /**
   * Get the JDBC SQL Type for this field
   */
  def getTargetSQLType : int
  
  def validate: List[ValidationIssues]
  /**
   * Given the driver type, return the string required to create the column in the database
   */
  def fieldCreatorString(dbType: DriverType, colName: String): String
  
  def fieldCreatorString(dbType: DriverType): List[String]
  def name: String
  
  def asString: String
  
  def dbColumnCount: int
  
  def dbColumnNames(in : String): List[String]
  
  def dbColumnName: String
  
  /**
    * Should the field be indexed?
    */
  def dbIndexed_? : boolean
      
  /**
    * Is the field the table's primary key
    */
  def dbPrimaryKey_? : boolean
      
  /**
    * Is the field a foreign key reference
    */
  def dbForeignKey_? : boolean
      
  /**
    * Called when a column has been added to the database via Schemifier
    */
  def dbAddedColumn: Option[() => unit]
                            
                            /**
                               * Called when a column has indexed via Schemifier
                               */
                             def dbAddedIndex: Option[() => unit]
                                                       
                            
}

trait MappedField[T <: Any,O<:Mapper[O]] extends BaseMappedField {
  def ignoreField = false
  def defaultValue : T
  

  def getActualField(actual: O): MappedField[T, O] = actual.getSingleton.getActualField(actual, this)

  /**
   * Given the driver type, return the string required to create the column in the database
   */
  def fieldCreatorString(dbType: DriverType, colName: String): String
  
  def fieldCreatorString(dbType: DriverType): List[String] = dbColumnNames(name).map{c => fieldCreatorString(dbType, c)}
  
  private var _dirty_? = false
  def dirty_? = !dbPrimaryKey_? && _dirty_?
  protected def dirty_?(b : boolean) = _dirty_? = b

      /**
         * Called when a column has been added to the database via Schemifier
         */
       def dbAddedColumn: Option[() => unit] = None

       /**
          * Called when a column has indexed via Schemifier
          */
        def dbAddedIndex: Option[() => unit] = None                                 
  /**
   * override this method in indexed fields to indicate that the field has been saved
   */
  def dbIndexFieldIndicatesSaved_? = false
  
  def owner : O
  final def safe_? : boolean = {
    owner.safe_?
  }
  
  def writePermission_? = false
  def readPermission_? = false
  
  /**
   * pascal-style assignment for syntactic sugar
   */
  def :=(v : T) : T = {
    if (safe_? || writePermission_?) i_set_!(v)
    v
  }
  
  def :?=(v: Option[T]) : Option[T] = {
    v.foreach(tv => this := tv)
    v
  }
  
  /**
   * Assignment from the underlying type.  It's ugly, but:<br />
   * field() = new_value <br />
   * field := new_value <br />
   * field set new_value <br />
   * field.set(new_value) <br />
   * are all the same
   */
  def update(v: T) {
    this := v
  }
  
  private var _name : String = null
  
  final def i_name_! = {_name}
  
  final def name = synchronized {
    if (_name == null) owner.checkNames
    _name
  }
  final def setName_!(newName : String) : String = {
    if(safe_?) _name = newName.toLowerCase
    _name
  }
  
  def displayName = name
  
  def resetDirty {
    if (safe_?) dirty_?(false)
  }
  
  def dbDisplay_? = true
  
  /**
   * pascal-style assignment for syntactic sugar
   */
  def ::=(v : Any) : T
  
  /**
   * Create an input field for the item
   */
  def toForm : NodeSeq = {
    <input type='text' name={S.mapFunction(name, {s => this ::= s; true})} value={get.toString}/>
  }
  
  def set(value : T) : T = {
    if (safe_? || writePermission_?) i_set_!(value)
    else throw new Exception("Do not have permissions to set this field")
  }

  def set_?(value: Option[T]): Option[T] = {
    value.foreach(v => this.set(v))
    value
  }
  
  protected def i_set_!(value : T) : T
  
  def buildSetActualValue(accessor : Method, inst : AnyRef, columnName : String) : (O, AnyRef) => unit 
  def buildSetLongValue(accessor : Method, columnName : String) : (O, long, boolean) => unit 
  def buildSetStringValue(accessor : Method, columnName : String) : (O, String) => unit 
  def buildSetDateValue(accessor : Method, columnName : String) : (O, Date) => unit 
  def buildSetBooleanValue(accessor : Method, columnName : String) : (O, boolean, boolean) => unit 
  protected def getField(inst : O, meth : Method) = meth.invoke(inst, null).asInstanceOf[MappedField[T,O]];
  
  def get : T = {
    if (safe_? || readPermission_?) i_get_!
    else i_obscure_!(i_get_!)
  }
  
  protected def i_get_! : T
  
  // def changed_? = {i_get_! != defaultValue}
  
  protected def i_obscure_!(in : T) : T
  
  def asString = displayName + "=" + get match {
    case null => ""
    case v @ _ => v.toString}
  
  def dbColumnCount = 1
  
  def dbColumnNames(in : String) = if (dbColumnCount == 1) List(dbColumnName) else List(in.toLowerCase)
  
  def dbColumnName = name.toLowerCase

  def dbIndexed_? : boolean = false

  def dbPrimaryKey_? : boolean = false
      
      /**
         * Is the field a foreign key reference
         */
       def dbForeignKey_? : boolean = false

  def getJDBCFriendly(field : String) : Object
  
  def getJDBCFriendly: Object = getJDBCFriendly(dbColumnName)

  /**
   * Get the JDBC SQL Type for this field
   */
  def getTargetSQLType(field : String) : int = getTargetSQLType

  /**
   * Get the JDBC SQL Type for this field
   */
  def getTargetSQLType : int
  
  override def toString : String = {
    val t = get
    if (t == null) "" else t.toString
  }
  
  def validate : List[ValidationIssues] = Nil

  def convertToJDBCFriendly(value: T): Object

  def asHtml : Node = Text(asString)
}

object MappedField {
  implicit def mapToType[T, A<:Mapper[A]](in : MappedField[T, A]) : T = in.get
  // implicit def mapFromOption[T, A<:Mapper[A]](in : Option[MappedField[T, A]]) : MappedField[T,A] = in.get
}

case class ValidationIssues(field : BaseMappedField, msg : String)

trait IndexedField[O] requires BaseMappedField extends BaseIndexedField {
  def convertKey(in : String) : Option[O]
  def convertKey(in : int) : Option[O]
  def convertKey(in: long): Option[O]
  def convertKey(in : AnyRef) : Option[O];
  def makeKeyJDBCFriendly(in : O) : AnyRef
  def db_display_? = false
}

trait BaseIndexedField requires BaseMappedField {
  
}

/**
 * A trait that defines foreign key references
 */
trait BaseForeignKey extends BaseMappedField {
  /**
   * Is the key defined?
   */
  def defined_? : boolean
  //def dbIndexed_? = true
          
  //def dbForeignKey_? = true
  /**
   * get the object referred to by this foreign key
   */
     
  def dbKeyToTable: BaseMetaMapper
  def dbKeyToColumn: BaseMappedField
  
  /**
    * Called when Schemifier adds a foreign key.  Return a function that will be called when Schemifier
    * is done with the schemification.
    */
  def dbAddedForeignKey: Option[() => unit]
}

trait LifecycleCallbacks {
  def beforeValidation {}
  def beforeValidationOnCreate {}
  def beforeValidationOnUpdate {}
  def afterValidation {}
  def afterValidationOnCreate {}
  def afterValidationOnUpdate {}

  def beforeSave {}
  def beforeCreate {}
  def beforeUpdate {}

  def afterSave {}
  def afterCreate {}
  def afterUpdate {}

  def beforeDelete {}
  def afterDelete {}
}


