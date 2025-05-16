package app.aaps.pump.tandem.common.database

import app.aaps.pump.tandem.common.data.history.HistoryLogDto
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.tandem.common.database.data.entity.TandemHistoryRecordEntity
import javax.inject.Inject

// TODO HistoryMapper refactor this for Tandem  N-3 - this class might be throws away, since we already have DbConverter class
//    that does the same

@Deprecated("Use DbDataConverter instead")
class HistoryMapper @Inject constructor(var tandemPumpUtil: TandemPumpUtil, var aapsLogger: AAPSLogger) {

    fun domainToEntity(logDto: HistoryLogDto): TandemHistoryRecordEntity {

        // TODO DB implemenent
        aapsLogger.error(LTag.PUMP, "N/A domainToEntity - HistoryLogDto: \n${tandemPumpUtil.gson.toJson(logDto)}")

        val tandemHistoryRecordEntity = TandemHistoryRecordEntity(
            sequenceId = logDto.sequenceId,
            pumpSerial = logDto.pumpSerial,
            typeId = TODO(),
            pumpTime = TODO(),
            payload = TODO(),
            entitySubId = TODO(),
            createdAt = TODO(),
            updatedAt = TODO()
        )


        // val tandemHistoryRecordEntity = TandemHistoryRecordEntity(
        //     id = if (logDto.id == null) logDto.sequenceNum else logDto.id!!,
        //     serial = logDto.serial,
        //     historyTypeIndex = logDto.historyTypeIndex,
        //     historyType = logDto.historyType,
        //     sequenceNum = logDto.sequenceNum,
        //     dateTimeMillis = logDto.dateTimeMillis,
        //     payload = logDto.payload,
        //
        //     // temporaryBasalRecord = null,
        //     // bolusRecord = null,
        //     // tddRecord = null,
        //     // basalProfileRecord = null,
        //     // alarmRecord = null,
        //     // configRecord = null,
        //     // pumpStatusRecord = null,
        //
        //     dateTimeRecord = null,
        //
        //     createdAt = logDto.created,
        //     updatedAt = logDto.updated
        // )
        //
        // if (logDto.subObject!=null) {
        //     when (logDto.subObject) {
        //         is DateTimeChanged -> tandemHistoryRecordEntity.dateTimeRecord = logDto.subObject as DateTimeChanged
        //         else               -> aapsLogger.warn(LTag.PUMP, "Unknown subObject: ${logDto.subObject!!.javaClass.name}")
        //     }
        // }

        // if (eventDto.subObject is Bolus) {
        //     historyRecordEntity.bolusRecord = eventDto.subObject as Bolus
        // } else if (eventDto.subObject is TemporaryBasal) {
        //     historyRecordEntity.temporaryBasalRecord = eventDto.subObject as TemporaryBasal
        // } else if (eventDto.subObject is TotalDailyInsulin) {
        //     historyRecordEntity.tddRecord = eventDto.subObject as TotalDailyInsulin
        // } else if (eventDto.subObject is BasalProfile) {
        //     historyRecordEntity.basalProfileRecord = eventDto.subObject as BasalProfile
        // } else if (eventDto.subObject is Alarm) {
        //     historyRecordEntity.alarmRecord = eventDto.subObject as Alarm
        // } else if (eventDto.subObject is ConfigurationChanged) {
        //     historyRecordEntity.configRecord = eventDto.subObject as ConfigurationChanged
        // } else if (eventDto.subObject is PumpStatusChanged) {
        //     historyRecordEntity.pumpStatusRecord = eventDto.subObject as PumpStatusChanged
        // } else if (eventDto.subObject is DateTimeChanged) {
        //     //historyRecordEntity.dateTimeRecord = eventDto.subObject as DateTimeChanged
        // }
        //
        // if (eventDto.subObject2 != null) {
        //     historyRecordEntity.temporaryBasalRecord = eventDto.subObject2 as TemporaryBasal
        // }

        //     return null //historyRecordEntity;



        // TODO HistoryMapper implement domainToEntity  N-3
        return tandemHistoryRecordEntity
    }

    fun entityToDomain(entity: TandemHistoryRecordEntity): HistoryLogDto {

        aapsLogger.error(LTag.PUMP, "N/A entityToDomain - Entity: \n${tandemPumpUtil.gson.toJson(entity)}")

        val historyLogDto = HistoryLogDto(
            sequenceId = entity.sequenceId,
            pumpSerial = entity.pumpSerial,
            typeId = entity.typeId,
            //historyType = entity.historyType,
            //sequenceNum = entity.sequenceNum,
            pumpTime = entity.pumpTime,
            payload = entity.payload,
            //subObject = null,
            created = entity.createdAt,
            updated = entity.updatedAt
        )

        //
        // // TODO HistoryMapper include subObject2 - done, not tested   N-3
        //
        // if (entity.bolusRecord != null) {
        //     eventDto.subObject = entity.bolusRecord
        // } else if (entity.temporaryBasalRecord != null) {
        //     // if (entity.entryType == YpsoPumpEventType.DELIVERY_STATUS_CHANGED) {
        //     //     eventDto.subObject2 = entity.temporaryBasalRecord
        //     // } else {
        //     //     eventDto.subObject = entity.temporaryBasalRecord
        //     // }
        // } else if (entity.tddRecord != null) {
        //     eventDto.subObject = entity.tddRecord
        // } else if (entity.basalProfileRecord != null) {
        //     eventDto.subObject = entity.basalProfileRecord
        // } else if (entity.alarmRecord != null) {
        //     eventDto.subObject = entity.alarmRecord
        // } else if (entity.configRecord != null) {
        //     eventDto.subObject = entity.configRecord
        // } else if (entity.pumpStatusRecord != null) {
        //     eventDto.subObject = entity.pumpStatusRecord
        // } else if (entity.dateTimeRecord != null) {
        //     //eventDto.subObject = entity.dateTimeRecord
        // }
        //

        // TODO HistoryMapper  extend with new subObject's N-3
        return historyLogDto
    }


}