package com.kyanadei.ddns

object GoogleDomainsDynamicDNSClient {
    def main( args: Array[String] ) : Unit = {
        while(true){
            IPUpdater.update
            Thread.sleep(3600000L) // Repeat once an hour
        }
    }
} 
