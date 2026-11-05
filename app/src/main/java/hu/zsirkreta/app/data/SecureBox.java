package hu.zsirkreta.app.data;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Tokenek titkosítása (a webes Fernet-titkosítás megfelelője). A kulcs az Android Keystore-ban van, az appból
 * nem olvasható ki; a titkosított szöveg: base64(iv ‖ titkosított adat + GCM-címke).
 */
public final class SecureBox {
    private SecureBox() {}

    private static final String ALIAS = "zsirkreta_tokens";
    private static final String STORE = "AndroidKeyStore";

    private static synchronized SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(STORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }

    public static String enc(String plain) {
        if (plain == null || plain.isEmpty()) return null;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV(), ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer b = ByteBuffer.allocate(1 + iv.length + ct.length);
            b.put((byte) iv.length).put(iv).put(ct);
            return Base64.encodeToString(b.array(), Base64.NO_WRAP);
        } catch (Exception e) {
            android.util.Log.e("ZsirKreta", "titkosítás", e);
            return null;
        }
    }

    /** Hibás vagy (pl. visszaállított készüléken) már nem visszafejthető adatnál null. */
    public static String dec(String box) {
        if (box == null || box.isEmpty()) return null;
        try {
            ByteBuffer b = ByteBuffer.wrap(Base64.decode(box, Base64.NO_WRAP));
            byte[] iv = new byte[b.get() & 0xFF];
            b.get(iv);
            byte[] ct = new byte[b.remaining()];
            b.get(ct);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
