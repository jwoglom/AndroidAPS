package app.aaps.pump.tandem.common.driver.connector.response

import app.aaps.pump.common.driver.connector.commands.data.AdditionalResponseDataInterface
import com.jwoglom.pumpx2.pump.messages.MessageType
import com.jwoglom.pumpx2.pump.messages.annotations.MessageProps
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlarmStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.MalfunctionStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.MalfunctionStatusResponse

@MessageProps(opCode = 121, size = 11, type = MessageType.RESPONSE, characteristic = Characteristic.CURRENT_STATUS, request = MalfunctionStatusRequest::class)
class MalfunctionStatusDto: MalfunctionStatusResponse(), AdditionalResponseDataInterface {

}
