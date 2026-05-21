package app.aaps.pump.omnipod.common.di

import app.aaps.pump.omnipod.common.bledriver.comm.blessed.BlessedBleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.blessed.BlessedBleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

// Swap which class is on the right side of each @Binds to toggle between the
// Blessed Kotlin BLE driver and the legacy Android BLE driver.
//   Blessed (default): BlessedBleConnectionFactory + BlessedBleDeviceManager
//   Legacy:            LegacyBleConnectionFactory  + LegacyBleDeviceManager
//
// Both implementations satisfy BleConnectionFactory / BleDeviceManager, so the
// rest of the codebase is unaffected by the switch.
@Module
@Suppress("unused")
abstract class OmnipodCommonBleModule {

    @Binds
    @Singleton
    abstract fun bindBleConnectionFactory(impl: BlessedBleConnectionFactory): BleConnectionFactory
    // abstract fun bindBleConnectionFactory(impl: LegacyBleConnectionFactory): BleConnectionFactory

    @Binds
    @Singleton
    abstract fun bindBleDeviceManager(impl: BlessedBleDeviceManager): BleDeviceManager
    // abstract fun bindBleDeviceManager(impl: LegacyBleDeviceManager): BleDeviceManager
}
