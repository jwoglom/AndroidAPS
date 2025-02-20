package app.aaps.pump.tandem.common.driver.connector.def;

import app.aaps.pump.common.driver.connector.commands.data.AdditionalResponseDataInterface;

public class ControlCommandResponse implements AdditionalResponseDataInterface {
    Integer id;
    Integer status;

    public ControlCommandResponse(Integer id, Integer status) {
        this.id = id;
        this.status = status;
    }
}
