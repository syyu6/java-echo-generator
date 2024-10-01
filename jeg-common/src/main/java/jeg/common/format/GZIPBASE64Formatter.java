package jeg.common.format;

import jeg.common.config.Config;
import jeg.common.util.Base64Util;

public class GZIPBASE64Formatter implements IFormatter {
    @Override
    public byte[] transform(byte[] bytes, Config config) throws Exception {
        return Base64Util.encodeToBase64(bytes).replace("\n", "").replace("\r", "").getBytes();
    }
}