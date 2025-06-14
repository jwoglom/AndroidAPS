package app.aaps.core.data.pump.defs

import app.aaps.core.data.pump.defs.Capability.*

enum class PumpCapability {

    // grouped by pump
    MDI(arrayOf(Bolus)),
    VirtualPumpCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery)),
    ComboCapabilities(arrayOf(Bolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad)),
    DanaCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad)),

    DanaWithHistoryCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad)),
    InsightCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, BasalRate30min)),
    MedtronicCapabilities(arrayOf(Bolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD)),
    OmnipodCapabilities(arrayOf(Bolus, TempBasal, BasalProfileSet, BasalRate30min)),
    YpsomedCapabilities(arrayOf(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad)),  // BasalRates (separately grouped)
    DiaconnCapabilities(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, Refill, ReplaceBattery, TDD, ManualTDDLoad), //
    EopatchCapabilities(Bolus, ExtendedBolus, TempBasal, BasalProfileSet, BasalRate30min),
    MedtrumCapabilities(Bolus, TempBasal, BasalProfileSet, BasalRate30min, TDD), // Technically the pump supports ExtendedBolus, but not implemented (yet)
    TandemSlimCapabilities(arrayOf(Refill, ReplaceBattery)),
    TandemMobiCapabilities(Bolus, TempBasal, BasalProfileSet, Refill), // TODO WIP
    ;

    var children: ArrayList<Capability> = ArrayList()

    constructor(list: Array<Capability>) {
        children.addAll(list)
    }

    constructor(vararg capabilities: Capability) {
        for (capability in capabilities) {
            children.add(capability)
        }
    }

    fun hasCapability(capability: Capability): Boolean = children.contains(capability)
}
