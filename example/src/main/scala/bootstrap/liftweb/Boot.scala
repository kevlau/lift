package bootstrap.liftweb

/*                                                *\
  (c) 2007 WorldWide Conferencing, LLC
  Distributed under an Apache License
  http://www.apache.org/licenses/LICENSE-2.0
\*                                                 */

import net.liftweb.util.Helpers
import net.liftweb.http._
import Helpers._
import net.liftweb.mapper.{DB, ConnectionManager, Schemifier, DefaultConnectionIdentifier, ConnectionIdentifier}
import java.sql.{Connection, DriverManager}
import net.liftweb.example.controller.WebServices
import javax.servlet.http.{HttpServlet, HttpServletRequest , HttpServletResponse, HttpSession}
import scala.collection.immutable.TreeMap
import net.liftweb.example.model._
 
/**
  * A class that's instantiated early and run.  It allows the application
  * to modify lift's environment
  */
class Boot {
  def boot {
    DB.defineConnectionManager(DefaultConnectionIdentifier, DBVendor)
    addToPackages("net.liftweb.example")
     
    Schemifier.schemify(User, WikiEntry)
    
    val dispatcher: Servlet.DispatchPf = {
      // if the url is "showcities" then return the showCities function
      case RequestMatcher(_, ParsePath("showcities":: _, _, _)) => XmlServer.showCities

      // if the url is "showstates" "curry" the showStates function with the optional second parameter
      case RequestMatcher(_, ParsePath("showstates":: xs, _, _)) => XmlServer.showStates(if (xs.isEmpty) "default" else xs.head)
      
      // if it's a web service, pass it to the web services invoker
      case RequestMatcher(r, ParsePath("webservices" :: c :: _, _,_)) => invokeWebService(r, c)
    }
    Servlet.addDispatchBefore(dispatcher)
    
    val rewriter: Servlet.RewritePf = {
      case RewriteRequest(_, path @ ParsePath("wiki" :: page :: _, _,_), _, _) => 
         RewriteResponse("/wiki", ParsePath("wiki" :: Nil, true, false), 
          TreeMap("wiki_page" -> page :: path.path.drop(2).zipWithIndex.map(p => ("param"+(p._2 + 1)) -> p._1) :_*))
    }
    
    Servlet.addRewriteBefore(rewriter)
  }
  
  private def invokeWebService(request: RequestState, methodName: String)(req: HttpServletRequest): Option[ResponseIt] =
      createInvoker(methodName, new WebServices(request, req)).flatMap(_() match {
      case ret: ResponseIt => Some(ret)
      case _ => None
    })
}

object XmlServer {
  def showStates(which: String)(req: HttpServletRequest): Option[XmlResponse] = Some(XmlResponse(
      <states renderedAt={timeNow.toString}>{
      which match {
        case "red" => <state name="Ohio"/><state name="Texas"/><state name="Colorado"/>
        
        case "blue" => <state name="New York"/><state name="Pennsylvania"/><state name="Vermont"/>
        
        case _ => <state name="California"/><state name="Rhode Island"/><state name="Maine"/>
      } }</states>))
 
  def showCities(ignore: HttpServletRequest): Option[XmlResponse] = Some(XmlResponse(<cities>
  <city name="Boston"/>
  <city name="New York"/>
  <city name="San Francisco"/>
  <city name="Dallas"/>
  <city name="Chicago"/>
  </cities>))
  
}

object DBVendor extends ConnectionManager {
  def newConnection(name: ConnectionIdentifier): Option[Connection] = {
    try {
      Class.forName("org.apache.derby.jdbc.EmbeddedDriver")
      val dm =  DriverManager.getConnection("jdbc:derby:lift_example;create=true")
      Some(dm)
    } catch {
      case e : Exception => e.printStackTrace; None
    }
  }
  def releaseConnection(conn: Connection) {conn.close}
}
