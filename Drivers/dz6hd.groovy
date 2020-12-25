/**
 *  Copyright 2020 Leon Schwartz
 *  Original (Smartthings) code Copyright 2017 Jason Xia
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
    definition (name: "Leviton DZ6HD Dimmer (LS)", namespace: "octadox", author: "Leon Schwartz", ocfDeviceType: "oic.d.light") {
        capability "Actuator"
        capability "Configuration"
        //capability "Health Check"
        capability "Indicator"
        capability "Light"
        capability "Polling"
        capability "Refresh"
        capability "Sensor"
        capability "Switch"
        capability "Switch Level"
        
        //Nightlight-related commands
        command "toggleNightlight"
        command "turnOffNightlight"
        command "turnOnNightlight"
        command "low"
        command "medium"
        command "high"
        command "levelUp"
        command "levelDown"
        
        attribute "loadType", "enum", ["incandescent", "led", "cfl"]
        attribute "presetLevel", "number"
        attribute "minLevel", "number"
        attribute "maxLevel", "number"
        attribute "fadeOnTime", "number"
        attribute "fadeOffTime", "number"
        attribute "levelIndicatorTimeout", "number"

        fingerprint mfr:"001D", prod:"3201", model:"0001", deviceJoinName: "Leviton Decora Z-Wave Plus 600W Dimmer"
        fingerprint mfr:"001D", prod:"3301", model:"0001", deviceJoinName: "Leviton Decora Z-Wave Plus 1000W Dimmer"
    }

	//Set up for switch
    preferences {
    	input("nightlightDimLevel", "number", title: "Dim level to use as nightlight (%):", required: false, displayDuringSetup: true, defaultValue: 2)
	input("loadType", "enum", options: [
				"0": "Incandescent",
				"1": "LED",
				"2": "CFL"], title: "Load type", defaultValue:"1", required:true, displayDuringSetup: true)
	input("indicatorStatus", "enum", options: [
				"255": "When Off",
				"254": "When On",
				"0": "Never On"], title: "Indicator", defaultValue:"255",required:true, displayDuringSetup: true)
	input(name: "presetLevel", type: "number", range: "0..100", title: "Always turn on to this (default is 100)", description: "0 = last dim level (default)\n1 - 100 = fixed level", displayDuringSetup:true, required:false, defaultValue: 100)
	input(name: "minLevel", type: "number", range: "0..100", title: "Minimum dimmer setting (default is 10)", displayDuringSetup:true, required:false, defaultValue: 1)
	input(name: "maxLevel", type: "number", range: "0..100", title: "Maximum dimmer setting (default is 100)", displayDuringSetup:true, required:false, defaultValue: 100)		
	input(name: "fadeOnTime", type: "number", range: "0..253", title: "Fade on time", description: "0 = instant on\n1 - 127 = 1 - 127 seconds (default 0)\n128 - 253 = 1 - 126 minutes", displayDuringSetup:true, required:false, defaultValue: 0)
	input(name: "fadeOffTime", type: "number", range: "0..253", title: "Fade off time", description: "0 = instant on\n1 - 127 = 1 - 127 seconds (default 0)\n128 - 253 = 1 - 126 minutes", displayDuringSetup:true, required:false, defaultValue: 0)
	input(name: "levelIndicatorTimeout", type: "number", title: "Dim level indicator timeout", description: "0 = dim level indicator off\n1 - 254 = timeout in seconds (default 3)\n255 = dim level indicator always on", range: "0..255", displayDuringSetup: false, defaultValue: 3, required: false)
	input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false)	

    }
}

def installed() {
    //log.debug "installed..."
    configure()
    response(refresh())
}

def updated() {
    //Ignore double-update events
    if (state.lastUpdatedAt != null && state.lastUpdatedAt >= now() - 1000) {
        //log.debug "ignoring double updated"
        return
    }
    //log.debug "updated..."
    state.lastUpdatedAt = now()

    initialize()
    response(configure())
}

//Configure the switch parameters
def configure() {
    def commands = []
    
    commands << secureCmd(zwave.configurationV1.configurationSet(parameterNumber: 1, scaledConfigurationValue: fadeOnTime).format())
    commands << zwave.configurationV1.configurationGet(parameterNumber: 1).format()
    commands << secureCmd(zwave.configurationV1.configurationSet(parameterNumber: 2, scaledConfigurationValue: fadeOffTime).format())
    commands << zwave.configurationV1.configurationGet(parameterNumber: 2).format()
    commands << secureCmd(zwave.configurationV1.configurationSet(parameterNumber: 3, scaledConfigurationValue: minLevel).format())
    commands << zwave.configurationV1.configurationGet(parameterNumber: 3).format()
    commands << secureCmd(zwave.configurationV1.configurationSet(parameterNumber: 4, scaledConfigurationValue: maxLevel).format())
    commands << zwave.configurationV1.configurationGet(parameterNumber: 4).format()
    commands << secureCmd(zwave.configurationV1.configurationSet(parameterNumber: 5, scaledConfigurationValue: presetLevel).format())
    commands << zwave.configurationV1.configurationGet(parameterNumber: 5).format()
    commands << secureCmd(zwave.configurationV1.configurationSet(parameterNumber: 6, scaledConfigurationValue: levelIndicatorTimeout).format())
    commands << zwave.configurationV1.configurationGet(parameterNumber: 6).format()
    commands << secureCmd(zwave.configurationV1.configurationSet(parameterNumber: 7, scaledConfigurationValue: indicatorStatus.toInteger()).format())
    commands << zwave.configurationV1.configurationGet(parameterNumber: 7).format()
    commands << secureCmd(zwave.configurationV1.configurationSet(parameterNumber: 8, scaledConfigurationValue: loadType.toInteger()).format())
    commands << zwave.configurationV1.configurationGet(parameterNumber: 8).format()
            
    delayBetween(commands,1000)
}

//Process messages from the device
def parse(String description) {
    def result = null
    def cmd = zwave.parse(description, [0x20: 1, 0x25:1, 0x26: 1, 0x70: 1, 0x72: 2])
//    def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x56: 1, 0x70: 2, 0x72: 2, 0x85: 2])
    if (cmd) {
        result = zwaveEvent(cmd)
        if (logEnable) log.debug "Parsed $cmd to $result"
    } else {
        log.debug "Non-parsed event: $description"
    }
    result
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

//Turn the switch on.
def on() {
    def fadeOnTime = device.currentValue("fadeOnTime")
    def presetLevel = device.currentValue("presetLevel")

    short duration = fadeOnTime == null ? 255 : fadeOnTime
    short level = presetLevel == null || presetLevel == 0 ? 0xFF : toZwaveLevel(presetLevel as short)
    setLevel(level, duration)
}

//Turn the switch off.
def off() {
    def fadeOffTime = device.currentValue("fadeOffTime")
    short duration = fadeOffTime == null ? 255 : fadeOffTime
    //If there's a local nightlight on, then instead of turning off, go to that value.
    if ( state.localNightlightOn ) {
		dimTheLight()
    }
    //Otherwise, turn off.
    else {
    	setLevel(0, duration)
	}
}

//Set the dimmer to the level provided
def setLevel(value, durationSeconds = null) {
    //log.debug "setLevel >> value: $value, durationSeconds: $durationSeconds"
    short level = toDisplayLevel(value as short)
    short dimmingDuration = durationSeconds == null ? 255 : secondsToDuration(durationSeconds as int)
    //If we are not turning off
	if (level > 0) {
		//If level is higher than nightlightDimLevel, then do not say NIGHT for the light anymore.
        if (level > nightlightDimLevel) {
        	state.isNightlightOn = false
            state.OnButtonName = "on"
        }
        //Update the display in the app (Night/On)
        sendEvent(name: "switch", value: state.OnButtonName)      	
    } else {
		//Update display in the app (Off)
        sendEvent(name: "switch", value: "off")
	}
    //Send the event for the level we are going to, as well.
    sendEvent(name: "level", value: level, unit: "%")
    
    //Send the event to the device, as well.
    delayBetween([zwave.switchMultilevelV2.switchMultilevelSet(value: toZwaveLevel(level), dimmingDuration: dimmingDuration).format(),
                  zwave.switchMultilevelV1.switchMultilevelGet().format()], durationToSeconds(dimmingDuration) * 1000 + commandDelayMs)
}

def poll() {
    delayBetween(statusCommands, commandDelayMs)
}

def ping() {
    poll()
}

def refresh() {
    def commands = statusCommands
    if (getDataValue("MSR") == null) {
        commands << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
    }
    for (i in 1..8) {
        commands << zwave.configurationV1.configurationGet(parameterNumber: i).format()
    }
    //log.debug "Refreshing with commands $commands"
    delayBetween(commands, commandDelayMs)
}

def low() {
    setLevel(10)
}

def medium() {
    setLevel(50)
}

def high() {
    setLevel(100)
}

def levelUp() {
    setLevel(device.currentValue("level") + (levelIncrement ?: defaultLevelIncrement))
}

def levelDown() {
    setLevel(device.currentValue("level") - (levelIncrement ?: defaultLevelIncrement))
}

private static int getCommandDelayMs() { 1000 }
private static int getDefaultLevelIncrement() { 10 }

private initialize() {
    // Device-Watch simply pings if no device events received for 32min(checkInterval)
    //sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    configure()
}

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	if (logEnable) log.debug "zwaveEvent(): CRC-16 Encapsulation Command received: ${cmd}"
	
	def newVersion = 1
	
	// SwitchMultilevel = 38 decimal
	// Configuration = 112 decimal
	// Manufacturer Specific = 114 decimal
	// Association = 133 decimal
	if (cmd.commandClass == 38) {newVersion = 3}
	if (cmd.commandClass == 112) {newVersion = 2}
	if (cmd.commandClass == 114) {newVersion = 2}								 
	if (cmd.commandClass == 133) {newVersion = 2}		
	
	def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data, newVersion)
	if (encapsulatedCommand) {
       zwaveEvent(encapsulatedCommand)
   } else {
       log.warn "Unable to extract CRC16 command from ${cmd}"
   }
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	//apparently, this need not be called...
	dimmerEvent(cmd.value)          
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
    dimmerEvent(cmd.value)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStopLevelChange cmd) {
    [createEvent(name:"switch", value: state.OnButtonName), response(zwave.switchMultilevelV1.switchMultilevelGet().format())]
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (cmd.value == 0) {
        switchEvent(false)
    } else if (cmd.value == 255) {
        switchEvent(true)
    } else {
        log.debug "Bad switch value $cmd.value"
    }
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    def result = null
    def reportValue = cmd.configurationValue[0]
    switch (cmd.parameterNumber) {
        case 1:
            result = createEvent(name: "fadeOnTime", value: reportValue)
            break
        case 2:
            result = createEvent(name: "fadeOffTime", value: reportValue)
            break
        case 3:
            result = createEvent(name: "minLevel", value: reportValue)
            break
        case 4:
            result = createEvent(name: "maxLevel", value: reportValue)
            break
        case 5:
            result = createEvent(name: "presetLevel", value: reportValue)
            break
        case 6:
            result = createEvent(name: "levelIndicatorTimeout", value: reportValue)
            break
        case 7:
            def value = reportValue == 0 ? "Never On" : reportValue == 254 ? "When On" : "When Off"
            result = createEvent(name: "indicatorStatus", value: value)
            break
        case 8:
            def value = reportValue == 0 ? "Incandescent" : reportValue == 1 ? "LED" : "CFL"
            result = createEvent(name: "loadType", value: value)
            break
    }
    result
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    log.debug "manufacturerId:   $cmd.manufacturerId"
    log.debug "manufacturerName: $cmd.manufacturerName"
    log.debug "productId:        $cmd.productId"
    log.debug "productTypeId:    $cmd.productTypeId"
    def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
    updateDataValue("MSR", msr)
    updateDataValue("manufacturer", cmd.manufacturerName)
    createEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
    [createEvent(name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false), response(zwave.switchMultilevelV1.switchMultilevelGet().format())]
    //log.debug ("COMMAND VAL: {$cmd.value}")
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.warn "${device.displayName} received unhandled command: ${cmd}"
}

//Physical event processor for dimmer events
private dimmerEvent(short level) {
    def result = null
    if (level == 0) {
        //If we're in night light mode, then go back to nightlight level.
        if (state.localNightlightOn) {
            short dimmingDuration = durationSeconds == null ? 255 : secondsToDuration(durationSeconds as int)
            level = toDisplayLevel(nightlightDimLevel as short)
    		state.isNightlightOn = true
            state.OnButtonName = "night"
        	
            //Create events to update the app.
            
            //Update the button name to NIGHT
            sendEvent(name: "switch", value: state.OnButtonName)
            //Encode update event to update level to NIGHT
            result = createEvent(name: "level", value: toDisplayLevel(level), unit: "%")
            //Update the switch to go back to NIGHT mode.
            result = [result, response([zwave.switchMultilevelV2.switchMultilevelSet(value: toZwaveLevel(level), dimmingDuration: dimmingDuration).format(), "delay 5000",
                  zwave.switchMultilevelV1.switchMultilevelGet().format()])]
       	} else {
	        result = [createEvent(name: "level", value: 0, unit: "%"), switchEvent(false)]
		}
	} 
    else if (level > 0 && level <= 100) {
        if (level > nightlightDimLevel) {
        	state.isNightlightOn = false
            state.OnButtonName = "on"
        	//Update the button nbame to ON
            sendEvent(name: "switch", value: state.OnButtonName)
        }
        //Encode update event to update level to the right level
        result = createEvent(name: "level", value: toDisplayLevel(level), unit: "%")

		//Force a switch check.
		if (device.currentValue("switch") != state.OnButtonName ) {
            // Don't blindly trust level. Explicitly request on/off status.
            result = [result, response(zwave.switchBinaryV1.switchBinaryGet().format())]
        }
    } else {
        log.debug "Bad dimming level $level"
    }
    result
}

private switchEvent(boolean on) {
// log.debug "HHHFFFFF"
 	if (!on && state.localNightlightOn) {
    	state.isNightlightOn = true
        state.OnButtonName = "night"
        setLevel(nightlightDimLevel)      
	} else {
	    createEvent(name: "switch", value: on ? state.OnButtonName : "off")
    }
}

private getStatusCommands() {
    [
            // Even though SwitchBinary is not advertised by this device, it seems to be the only way to assess its true
            // on/off status.
            zwave.switchBinaryV1.switchBinaryGet().format(),
            zwave.switchMultilevelV1.switchMultilevelGet().format()
    ]
}

private short toDisplayLevel(short level) {
    level = Math.max(0, Math.min(100, level))
    (level == (short) 99) ? 100 : level
}

private short toZwaveLevel(short level) {
    Math.max(0, Math.min(99, level))
}

private int durationToSeconds(short duration) {
    if (duration >= 0 && duration <= 127) {
        duration
    } else if (duration >= 128 && duration <= 254) {
        (duration - 127) * 60
    } else if (duration == 255) {
        2   // factory default
    } else {
        log.error "Bad duration $duration"
        0
    }
}

private short secondsToDuration(int seconds) {
    if (seconds >= 0 && seconds <= 127) {
        seconds
    } else if (seconds >= 128 && seconds <= 127 * 60) {
        127 + Math.round(seconds / 60)
    } else {
        log.error "Bad seconds $seconds"
        255
    }
}

//LS: additional methods added
def resetLevel() {
    //If switch is higher than nightlight, don't turn it off. NOTE: THIS DOES NOT WORK.
    if ( state.isNightlightOn) {
    	localResetLevel()
    	setLevel(0)
    } else {
    	localResetLevel()
    }
}

def localResetLevel()
{
	//no need to dim up and then down for this type of switch, so just turn it off instead of this: resetLevel(99)
	state.isNightlightOn = false
    state.isNightlightLimit = false
    state.localNightlightOn = false
    state.OnButtonName = "on"
    //setLevel(0)
}

def turnOnNightlight()
{
    localNightlightDim()
}

def turnOffNightlight() {
	//If switch is higher than nightlight, don't turn it off. NOTE: THIS DOES NOT WORK.
	if ( state.isNightlightOn) {
		localResetLevel()
		setLevel(0)
	} else {
		localResetLevel()
	}
}

def toggleNightlight()
{
    if (state.isNightlightOn)
    {
        turnOffNightlight()
    }
    else {
        localNightlightDim()
    }
}

def nightlightDim()
{
	state.isNightlightOn = true
    //If already on, do not touch for now, when turned off, it will do it's thing.
    if (device.currentValue("switch") != "on")
    {
    	localNightlightDim()
	}
}

def localNightlightDim()
{
	state.isNightlightOn = true
    state.localNightlightOn = true
    if (device.currentValue("switch") != "on") {
	    dimTheLight()
	}
}

def dimTheLight()
{
	state.OnButtonName = "night"
	setLevel(nightlightDimLevel)
}

def nightlightLimit()
{
	if (!state.isNightlightOn)
    {
    	state.isNightlightLimit = true
    	//If already on, do not touch for now, when turned off, it will do it's thing.
       	if (device.currentValue("switch") != "on")
        {
        	delayBetween ([setLevel(nightlightDimLevel),
					      off()], 15)
    	}
    }
}

int dimLevel()
{
	return nightlightDimLevel
}

String secureCmd(cmd) {
    if (getDataValue("zwaveSecurePairingComplete") == "true" && getDataValue("S2") == null) {
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
		return secure(cmd)
    }	
}

String secure(String cmd){
    return zwaveSecureEncap(cmd)
}

String secure(hubitat.zwave.Command cmd){
    return zwaveSecureEncap(cmd)
}
