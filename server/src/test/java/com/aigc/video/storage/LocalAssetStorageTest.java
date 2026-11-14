package com.aigc.video.storage;

import com.aigc.video.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAssetStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void convertsLocalReferenceImageToDataUri() {
        StorageProperties properties = new StorageProperties();
        properties.setLocalDir(tempDir.toString());
        LocalAssetStorage storage = new LocalAssetStorage(properties);
        byte[] content = {1, 2, 3, 4};
        StoredAsset stored = storage.store(content, "images/reference.png", "image/png");

        String providerUrl = storage.providerImageUrl(stored.url());

        assertTrue(providerUrl.startsWith("data:image/png;base64,"));
        assertArrayEquals(content, Base64.getDecoder().decode(
                providerUrl.substring("data:image/png;base64,".length())));
        assertEquals("/files/images/reference.png", stored.url());
    }

    @Test
    void keepsExternalReferenceImageUrl() {
        LocalAssetStorage storage = new LocalAssetStorage(new StorageProperties());
        String url = "https://example.com/reference.jpg";
        assertEquals(url, storage.providerImageUrl(url));
    }
}
