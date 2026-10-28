package art.pilipala.worker.gateway;

import java.io.IOException;

/** 成片/图片直链下载；与 TOS 写入分离，便于单测。 */
public interface MediaDownloader {

    byte[] download(String url) throws IOException;
}
