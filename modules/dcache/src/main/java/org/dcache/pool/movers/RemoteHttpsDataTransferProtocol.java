package org.dcache.pool.movers;

import diskCacheV111.util.CacheException;
import diskCacheV111.vehicles.ProtocolInfo;
import diskCacheV111.vehicles.RemoteHttpsDataTransferProtocolInfo;
import dmg.cells.nucleus.CellEndpoint;
import eu.emi.security.authn.x509.impl.KeyAndCertCredential;
import java.io.IOException;
import java.nio.file.OpenOption;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Set;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.dcache.pool.repository.RepositoryChannel;
import org.dcache.vehicles.FileAttributes;

/**
 * A mover for transferring a file using HTTP over a TLS/SSL connection.
 */
public class RemoteHttpsDataTransferProtocol extends RemoteHttpDataTransferProtocol {

    private final TrustManager[] trustManagers;
    private final SecureRandom secureRandom;

    private PrivateKey privateKey;
    private X509Certificate[] chain;

    public RemoteHttpsDataTransferProtocol(CellEndpoint cell, X509TrustManager trustManager,
          SecureRandom secureRandom) {
        super(cell);
        this.secureRandom = secureRandom;
        this.trustManagers = new TrustManager[]{trustManager};
    }

    @Override
    public void runIO(FileAttributes attributes, RepositoryChannel channel,
          ProtocolInfo genericInfo, Set<? extends OpenOption> access)
          throws CacheException, IOException, InterruptedException {
        RemoteHttpsDataTransferProtocolInfo info =
              (RemoteHttpsDataTransferProtocolInfo) genericInfo;
        privateKey = info.getPrivateKey();
        chain = info.getCertificateChain();
        super.runIO(attributes, channel, genericInfo, access);
    }

    @Override
    protected HttpClientBuilder customise(HttpClientBuilder builder) throws CacheException {
        try {
            KeyManager[] keyManagers;
            if (privateKey != null & chain != null) {
                KeyAndCertCredential credential = new KeyAndCertCredential(privateKey, chain);
                keyManagers = new KeyManager[]{credential.getKeyManager()};
            } else {
                keyManagers = new KeyManager[0];
            }
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers, trustManagers, secureRandom);
            return super.customise(builder).setSSLContext(context);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new CacheException("failed to build http client: " + e.getMessage(), e);
        }
    }
}
