package com.kyanadei.ddns

import scalaj.http.{Http, HttpOptions}
import play.api.libs.json._
import org.apache.commons.codec.binary.Base64
import scala.io.Source
import java.io.FileWriter
import org.apache.log4j.Logger
import java.nio.file.{Files, Paths}

class IPUpdater

object IPUpdater {
    /** Class variable for logger */
    private val logger:Logger = Logger.getLogger(classOf[IPUpdater])

    /** Simple class for interpreting host from JSON */
    private case class Host( name:String, username:String, password:String, status:String ) {
        def equals( obj:Host ):Boolean = obj.name == name
    }
    private implicit val hostReads = Json.reads[Host]   // Used for JSON serialization
    private implicit val hostWrites = Json.writes[Host]

    /** Simple class for interpreting all config from JSON */
    private case class HostConfig( ip:String, hosts:Seq[Host] )
    private implicit val allWrites = Json.writes[HostConfig]

    /** Gets the config (IP & hosts) from JSON */
    private def getConfig = Json.parse( Files.readAllBytes( Paths.get( "hosts.json" ) ) )

    /** Makes request for actual IP */
    private def getActualIP = Http( "http://checkip.amazonaws.com" ).asString.body.trim

    /** Grabs the stored IP */
    private def getStoredIP( config:JsValue ) = ( config \ "ip" ).asOpt[String].get

    /** Gets sequence of hosts */
    private def getHosts( config:JsValue ) = ( config \ "hosts" ).asOpt[Seq[Host]].get
    
    /** Gets hosts from config based on the given status */
    private def getHostsByStatus( config:JsValue, status:String ) = {
        getHosts( config ).filter( host => host.status == status )
    }

    /** Gets hosts from config except those with the given status */
    private def getHostsExcludeStatus( config:JsValue, status:String ) = {
        getHosts( config ).filter( host => host.status != status )
    }

    /** Handles the response from the POST request */
    private def handleResponse( response:String, host:Host ) = {
        val badResponses = List( "nohost", "badauth", "notfqdn", "badagent", "abuse", "911" )
        val responseFirst = response.trim.split( " " )( 0 )

        if( badResponses.contains( responseFirst ) )
            logger.error( "Response: " + response + "\tPlease check bad response" )
        else
            logger.info( "Response: " + response )

        val responseStatus = if( responseFirst == "good" || responseFirst == "nochg" ) "good" else responseFirst.trim
        Host( host.name, host.username, host.password, responseStatus )
    }

    /** Makes POST request to Google Domains based on the given info */
    private def updateGoogleWithIP( ip:String, hostname:String, authString:String ) = {
        logger.info( "Updating " + hostname )
        val url = "https://domains.google.com/nic/update"
        Http( url + "?hostname=" + hostname + "&myip=" + ip )
            .header( "Authorization", authString )
            .header(" User-Agent", "RCANADY" )
            .option( HttpOptions.readTimeout( 10000 ) )
            .asString
            .body
    }
    
    /** Updates the config file with the IP and hosts */
    private def writeConfigToFile( ip:String, hosts:Seq[Host] ) = {
        val json = Json.prettyPrint( Json.toJson( HostConfig( ip, hosts ) ) )
        val fileWriter = new FileWriter( "hosts.json" )
        fileWriter.write( json.toString )
        fileWriter.close
    }

    /** Calculates the base64 encoded basic auth string for the Authorization header */
    private def getEncodedAuthString( username:String, password:String ) = {
        "Basic " + Base64.encodeBase64URLSafeString( ( username + ":" + password ).getBytes )
    }

    /** Handles updates to each host */
    private def handleUpdate( hosts:Seq[Host], actualIP: String, config:JsValue ) = {
        // TODO - put this in an actor
        if( hosts.length == 0 )
            hosts
        else
            hosts.map( host => { // loop over all hosts
                var postResult = updateGoogleWithIP( actualIP, 
                                                     host.name,
                                                     getEncodedAuthString( host.username, host.password ) )
                handleResponse( postResult, host )
            } )
    }

    /** Gets hosts with given status and updates them accordingly */
    private def processUpdateWithStatus( status:String, ip:String, config:JsValue ) = {
        val oldHosts = getHostsExcludeStatus( config, status )
        val updatedHosts = handleUpdate( getHostsByStatus( config, status ), ip, config )
        oldHosts.union( updatedHosts )
    }

    /** Public method for updating all hosts with IP */
    def update = {
        //logger.info( "Checking for IP change and new domains" )

        val config = getConfig
        val actualIP = getActualIP

        val allHosts = if( actualIP == getStoredIP( config ).trim ) processUpdateWithStatus( "new", actualIP, config )
                        else processUpdateWithStatus( "good", actualIP, config )

        writeConfigToFile( actualIP, allHosts )
    }
}
