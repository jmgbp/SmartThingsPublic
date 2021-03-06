/**
 *  Fibaro Wall Plug G ZW5
 *
 *  Copyright 2017 Artur Draga
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Fibaro Wall Plug G ZW5", namespace: "ClassicGOD", author: "Artur Draga") {
		capability "Switch"
		capability "Energy Meter"
		capability "Power Meter"
		capability "Configuration"
		capability "Health Check"

		command "reset"
		command "refresh"

		fingerprint mfr: "010F", prod: "1801"
		fingerprint deviceId: "0x1001", inClusters:"0x5E,0x22,0x59,0x56,0x7A,0x32,0x71,0x73,0x98,0x85,0x70,0x72,0x5A,0x8E,0x25,0x86,0x55,0x9F,0x6C,0x75"
		fingerprint deviceId: "0x1001", inClusters:"0x5E,0x22,0x59,0x56,0x7A,0x32,0x71,0x73,0x85,0x70,0x72,0x5A,0x8E,0x25,0x86,0x55,0x9F,0x6C,0x75"
		
	}

	tiles (scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 3, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "off", label: 'Off', action: "switch.on", icon: "https://raw.githubusercontent.com/ClassicGOD/SmartThingsPublic/master/devicetypes/classicgod/fibaro-wall-plug-zw5-v2.src/images/plug0.png", backgroundColor: "#ffffff"
				attributeState "on", label: 'On', action: "switch.off", icon: "https://raw.githubusercontent.com/ClassicGOD/SmartThingsPublic/master/devicetypes/classicgod/fibaro-wall-plug-zw5-v2.src/images/plug2.png", backgroundColor: "#00a0dc"
			}
			tileAttribute("device.multiStatus", key:"SECONDARY_CONTROL") {
				attributeState("multiStatus", label:'${currentValue}')
			} 
		}
		valueTile("power", "device.power", decoration: "flat", width: 2, height: 2) {
			state "power", label:'${currentValue}\nW', action:"refresh"
		}
		valueTile("energy", "device.energy", decoration: "flat", width: 2, height: 2) {
			state "energy", label:'${currentValue}\nkWh', action:"refresh"
		}
		valueTile("reset", "device.energy", decoration: "flat", width: 2, height: 2) {
			state "reset", label:'reset\nkWh', action:"reset"
		}
	}

	preferences {
	
		input (
			title: "Fibaro Wall Plug manual",
			description: "Tap to view the manual.",
			image: "https://manuals.fibaro.com/wp-content/uploads/2018/06/wpg_icon.jpg",
			url: "https://manuals.fibaro.com/content/manuals/en/FGWPG-111/FGWPG-111-EN-T-v1.0.pdf",
			type: "href",
			element: "href"
		)
		
		parameterMap().each {
			input (
				title: "${it.num}. ${it.title}",
				description: it.descr,
				type: "paragraph",
				element: "paragraph"
			)
			
			input (
				name: it.key,
				title: null,
				description: "Default: $it.def" ,
				type: it.type,
				options: it.options,
				range: (it.min != null && it.max != null) ? "${it.min}..${it.max}" : null,
				defaultValue: it.def,
				required: false
			)
		}
		
		input ( name: "logging", title: "Logging", type: "boolean", required: false )
	}
}

//UI and tile functions
def on() {
	encap(zwave.basicV1.basicSet(value: 255))
}

def off() {
	encap(zwave.basicV1.basicSet(value: 0))
}

def reset() {
	def cmds = []
	cmds << zwave.meterV3.meterReset()
	cmds << zwave.meterV3.meterGet(scale: 0)
	cmds << zwave.meterV3.meterGet(scale: 2)
	encapSequence(cmds,1000)
}

def refresh() {
	def cmds = []
	cmds << zwave.meterV3.meterGet(scale: 0)
	cmds << zwave.meterV3.meterGet(scale: 2)
	cmds << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 4)
	encapSequence(cmds,1000)
}

//Configuration and synchronization
def updated() {
	if ( state.lastUpdated && (now() - state.lastUpdated) < 500 ) return
	def cmds = []
	logging("Executing updated()","info")

	if (device.currentValue("numberOfButtons") != 6) { sendEvent(name: "numberOfButtons", value: 6) }
	
	state.lastUpdated = now()
	syncStart()
}

def configure() {
	encap(zwave.basicV1.basicSet(value: 0))
}

private syncStart() {
	boolean syncNeeded = false
	Integer settingValue = null
	parameterMap().each {
		if(settings."$it.key" != null) {
			settingValue = settings."$it.key" as Integer
			if (state."$it.key" == null) { state."$it.key" = [value: null, state: "synced"] } 
			if (state."$it.key".value != settingValue || state."$it.key".state != "synced" ) {
				state."$it.key".value = settingValue
				state."$it.key".state = "notSynced"
				syncNeeded = true
			}
		}
	}
	if ( syncNeeded ) { 
		logging("sync needed.", "info")
		syncNext()
	}
}

private syncNext() {
	logging("Executing syncNext()","info")
	def cmds = []
	for ( param in parameterMap() ) {
		if ( state."$param.key"?.value != null && state."$param.key"?.state in ["notSynced","inProgress"] ) {
			multiStatusEvent("Sync in progress. (param: ${param.num})", true)
			state."$param.key"?.state = "inProgress"
			cmds << response(encap(zwave.configurationV2.configurationSet(configurationValue: intToParam(state."$param.key".value, param.size), parameterNumber: param.num, size: param.size)))
			cmds << response(encap(zwave.configurationV2.configurationGet(parameterNumber: param.num)))
			break
		} 
	}
	if (cmds) { 
		runIn(10, "syncCheck")
		sendHubCommand(cmds,1000)
	} else {
		runIn(1, "syncCheck")
	}
}

private syncCheck() {
	logging("Executing syncCheck()","info")
	def failed = []
	def incorrect = []
	def notSynced = []
	parameterMap().each {
		if (state."$it.key"?.state == "incorrect" ) {
			incorrect << it
		} else if ( state."$it.key"?.state == "failed" ) {
			failed << it
		} else if ( state."$it.key"?.state in ["inProgress","notSynced"] ) {
			notSynced << it
		}
	}
	
	if (failed) {
		multiStatusEvent("Sync failed! Verify parameter: ${failed[0].num}", true, true)
	} else if (incorrect) {
		multiStatusEvent("Sync mismatch! Verify parameter: ${incorrect[0].num}", true, true)
	} else if (notSynced) {
		multiStatusEvent("Sync incomplete! Open settings and tap Done to try again.", true, true)
	} else {
		if (device.currentValue("multiStatus")?.contains("Sync")) { multiStatusEvent("Sync OK.", true, true) }
	}
}

private multiStatusEvent(String statusValue, boolean force = false, boolean display = false) {
	if (!device.currentValue("multiStatus")?.contains("Sync") || device.currentValue("multiStatus") == "Sync OK." || force) {
		sendEvent(name: "multiStatus", value: statusValue, descriptionText: statusValue, displayed: display)
	}
}

//event handlers related to configuration and sync
def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
	def paramKey = parameterMap().find( {it.num == cmd.parameterNumber } ).key
	logging("Parameter ${paramKey} value is ${cmd.scaledConfigurationValue} expected " + state."$paramKey".value, "info")
	state."$paramKey".state = (state."$paramKey".value == cmd.scaledConfigurationValue) ? "synced" : "incorrect"
	syncNext()
}

def zwaveEvent(physicalgraph.zwave.commands.applicationstatusv1.ApplicationRejectedRequest cmd) {
	logging("rejected request!","warn")
	for ( param in parameterMap() ) {
		if ( state."$param.key"?.state == "inProgress" ) {
			state."$param.key"?.state = "failed"
			break
		} 
	}
}

//event handlers
def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	logging("${device.displayName} - BasicReport received, value: ${cmd.value}","info")
	sendEvent([name: "switch", value: (cmd.value == 0 ) ? "off": "on"])
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	logging("${device.displayName} - SwitchBinaryReport received, value: ${cmd.value}","info")
	sendEvent([name: "switch", value: (cmd.value == 0 ) ? "off": "on"])
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
	logging("${device.displayName} - SensorMultilevelReport received, value: ${cmd.scaledSensorValue} scale: ${cmd.scale}","info")
	if (cmd.sensorType == 4) { 
		sendEvent([name: "power", value: cmd.scaledSensorValue, unit: "W"]) 
		multiStatusEvent("${device.currentValue("power")} W / ${device.currentValue("energy")} kWh")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd) {
	logging("${device.displayName} - MeterReport received, value: ${cmd.scaledMeterValue} scale: ${cmd.scale}","info")
	switch (cmd.scale) {
		case 0: sendEvent([name: "energy", value: cmd.scaledMeterValue, unit: "kWh"]); break;
		case 2: sendEvent([name: "power", value: cmd.scaledMeterValue, unit: "W"]); break;
	}
	multiStatusEvent("${device.currentValue("power")} W / ${device.currentValue("energy")} kWh")
}

/*
####################
## Z-Wave Toolkit ##
####################
*/
def parse(String description) {	  
	def result = []
	logging("${device.displayName} - Parsing: ${description}")
	if (description.startsWith("Err 106")) {
		result = createEvent(
			descriptionText: "Failed to complete the network security key exchange. If you are unable to receive data from it, you must remove it from your network and add it again.",
			eventType: "ALERT",
			name: "secureInclusion",
			value: "failed",
			displayed: true,
		)
	} else if (description == "updated") {
		return null
	} else {
		def cmd = zwave.parse(description, cmdVersions()) 
		if (cmd) {
			logging("${device.displayName} - Parsed: ${cmd}")
			zwaveEvent(cmd)
		}
	}
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand(cmdVersions()) 
	if (encapsulatedCommand) {
		logging("${device.displayName} - Parsed SecurityMessageEncapsulation into: ${encapsulatedCommand}")
		zwaveEvent(encapsulatedCommand)
	} else {
		log.warn "Unable to extract secure cmd from $cmd"
	}
}

def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	def version = cmdVersions()[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (encapsulatedCommand) {
		logging("${device.displayName} - Parsed Crc16Encap into: ${encapsulatedCommand}")
		zwaveEvent(encapsulatedCommand)
	} else {
		log.warn "Could not extract crc16 command from $cmd"
	}
}

private logging(text, type = "debug") {
	if (settings.logging == "true") {
		log."$type" text
	}
}

private secEncap(physicalgraph.zwave.Command cmd) {
	logging("${device.displayName} - encapsulating command using Secure Encapsulation, command: $cmd","info")
	zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private crcEncap(physicalgraph.zwave.Command cmd) {
		logging("${device.displayName} - encapsulating command using CRC16 Encapsulation, command: $cmd","info")
		zwave.crc16EncapV1.crc16Encap().encapsulate(cmd).format() 
}

private encap(physicalgraph.zwave.Command cmd) {
	if (zwaveInfo.zw.contains("s")) { 
		secEncap(cmd)
	} else if (zwaveInfo.cc.contains("56")){ 
		crcEncap(cmd)
	} else {
		logging("${device.displayName} - no encapsulation supported for command: $cmd","info")
		cmd.format()
	}
}

private encapSequence(cmds, Integer delay=250) {
	delayBetween(cmds.collect{ encap(it) }, delay)
}

private List intToParam(Long value, Integer size = 1) {
	def result = []
	size.times { 
		result = result.plus(0, (value & 0xFF) as Short)
		value = (value >> 8)
	}
	return result
}
/*
##########################
## Device Configuration ##
##########################
*/
private Map cmdVersions() {
	[0x5E: 2, 0x22: 1, 0x59: 1, 0x56: 1, 0x7A: 1, 0x32: 3, 0x71: 1, 0x73: 1, 0x98: 1, 0x31: 5, 0x85: 2, 0x70: 2, 0x72: 2, 0x5A: 1, 0x8E: 2, 0x25: 1, 0x86: 2] //Fibaro Wall Plug ZW5
}

private parameterMap() {[
	[key: "restoreState", num: 2, size: 1, type: "enum", options: [0: "0 - device remains switched off", 1: "1 - device restores the state"], def: "1", title: "Restore state after power failure", 
		descr: "After the power supply is back on, the Wall Plug can be restored to previous state or remain switched off."],
	[key: "overloadSafety", num: 3, size: 2, type: "number", def: 0, min: 0, max: 30000 , title: "Overload safety switch", 
		descr: "Allows to turn off the controlled device in case of exceeding the defined power; 1-3000 W.\n0 - function inactive\n10-30000 (1.0-3000.0W, step 0.1W)"],
	[key: "standardPowerReports", num: 11, size: 1, type: "number", def: 15, min: 1, max: 100, title: "Power reporting", 
		descr: "This parameter determines the minimum percentage change in active power that will result in sending a power report.\n1-99 - power change in percent\n100 - reports are disabled"], 
	[key: "periodicPowerReports", num: 13, size: 2, type: "number", def: 3600, min: 0, max: 32400, title: "Periodic power reports", 
		descr: "Time period between independent reports.\n0 - periodic reports inactive\n5-32400 (in seconds)"], 
	[key: "periodicEnergyReports", num: 14, size: 2, type: "number", def: 3600, min: 0, max: 32400, title: "Periodic energy reports", 
		descr: "Time period between independent reports.\n0 - periodic reports inactive\n5-32400 (in seconds)"], 
	[key: "ringColorOn", num: 41, size: 1, type: "enum", options: [
		0: "0 - Off",
		1: "1 - Load based - continuous", 
		2: "2 - Load based - steps", 
		3: "3 - White", 
		4: "4 - Red", 
		5: "5 - Green", 
		6: "6 - Blue", 
		7: "7 - Yellow", 
		8: "8 - Cyan", 
		9: "9 - Magenta"
		], def: "1", title: "Ring LED color when on", descr: "Ring LED colour when the device is ON."],
	[key: "ringColorOff", num: 42, size: 1, type: "enum", options: [
		0: "0 - Off",
		1: "1 - Last measured power",  
		3: "3 - White", 
		4: "4 - Red", 
		5: "5 - Green", 
		6: "6 - Blue", 
		7: "7 - Yellow", 
		8: "8 - Cyan", 
		9: "9 - Magenta"
		], def: "0", title: "Ring LED color when off", descr: "Ring LED colour when the device is OFF."]
]}
