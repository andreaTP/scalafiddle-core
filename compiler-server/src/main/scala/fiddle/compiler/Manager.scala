package fiddle.compiler

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.stream.scaladsl.Flow
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.concurrent.duration._

case object CompilerTerminated

class Manager extends Actor with ActorLogging {
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()
  import context.dispatcher

  def connect(): Unit = {
    // WebSocket flow
    val wsFlow: Flow[Message, Message, Any] = ActorFlow.actorRef[Message, Message](out => CompileActor.props(null, out, context.self), overflowStrategy = OverflowStrategy.fail)
    val (upgradeResponse, _) = Http().singleWebSocketRequest(WebSocketRequest(Config.routerUrl), wsFlow)
    upgradeResponse.map { upgrade =>
      // just like a regular http request we can access response status which is available via upgrade.response.status
      // status code 101 (Switching Protocols) indicates that server support WebSockets
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        log.info(s"Connection succeeded")
      } else {
        log.info(s"Connection failed: ${upgrade.response.status}")
      }
    } recover {
      case e =>
        log.info(s"Connection failed: $e")
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    // connect to ScalaFiddle Router
    connect()
  }

  def receive = {
    case CompilerTerminated =>
      log.debug("Compiler terminated")
      system.scheduler.scheduleOnce(3.seconds)(connect())
  }
}