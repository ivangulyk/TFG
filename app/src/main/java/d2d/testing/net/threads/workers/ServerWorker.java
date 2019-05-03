package d2d.testing.net.threads.workers;

import java.nio.channels.SelectableChannel;

import d2d.testing.helpers.Logger;
import d2d.testing.net.handlers.FileHandler;
import d2d.testing.net.handlers.StreamHandler;
import d2d.testing.net.packets.DataPacket;
import d2d.testing.net.threads.selectors.AbstractSelector;

public class ServerWorker extends AbstractWorker {
    private StreamHandler mStream = null;

    @Override
    protected void processData(DataPacket dataPacket, AbstractSelector selector, SelectableChannel channel) {
        switch (dataPacket.getType())
        {
            case DataPacket.TYPE_MSG:
                Logger.d("ServerWorker received TYPE_MSG command");
                selector.getMainActivity().updateMsg(new String(dataPacket.getBodyData()));

                Logger.d("ServerWorker echoing the MSG");
                selector.send(dataPacket.getData());
                break;
            case DataPacket.STREAM_ON:
                selector.getMainActivity().updateStreamList(true, new String(dataPacket.getBodyData()));
                Logger.d("ServerWorker received STREAM_ON command");

                selector.send(dataPacket.getData());
                Logger.d("ServerWorker echoing the STREAM_ON");
                break;
            case DataPacket.STREAM_OFF:
                selector.getMainActivity().updateStreamList(false, new String(dataPacket.getBodyData()));
                Logger.d("ServerWorker received STREAM_OFF command");

                selector.send(dataPacket.getData());
                Logger.d("ServerWorker echoing the STREAM_OFF");
                break;
            case DataPacket.TYPE_FILE:
                selector.getMainActivity().getWiFiP2pPermissions().memory();
                if(selector.getMainActivity().get_storage_has_perm()) {
                    FileHandler.handle(dataPacket);
                } else {
                    //todo perdemos el archivo si no tenemos permisos
                    //TODO colas en permisos
                    Logger.d("ServerWorker received TYPE_FILE command but no permission we are losing the file fix");
                }
                break;

            case DataPacket.TYPE_VIDEO_STREAM:
                Logger.d("ClientWorker received TYPE_VIDEO_STREAM");
                if(mStream == null)
                {
                    mStream = new StreamHandler();
                }

                mStream.handle(dataPacket);
                break;

            default:
                Logger.e("ClientWorker received no TYPE_FILE");
                //ERROR NO HAY TIPO DE MENSAJE!!
        }
    }
}