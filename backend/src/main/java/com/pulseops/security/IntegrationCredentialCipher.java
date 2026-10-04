package com.pulseops.security;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IntegrationCredentialCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public IntegrationCredentialCipher(@Value("${pulseops.integrations.encryption-key:${pulseops.security.jwt.secret}}") String secret) {
        if(secret.length()<32) throw new IllegalArgumentException("A chave de integrações deve ter pelo menos 32 caracteres");
        try { key=new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("pulseops-integration-v1:"+secret).getBytes(StandardCharsets.UTF_8)),"AES"); }
        catch(GeneralSecurityException exception){throw new IllegalStateException("Criptografia indisponível",exception);}
    }
    public String encrypt(String value) {
        try { byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));return Base64.getEncoder().encodeToString(nonce)+"."+Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8))); }
        catch(GeneralSecurityException exception){throw new IllegalStateException("Não foi possível proteger a credencial",exception);}
    }
    public String decrypt(String value) {
        if(value==null) return null;
        try {String[] parts=value.split("\\.",2);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);}
        catch(GeneralSecurityException | IllegalArgumentException | ArrayIndexOutOfBoundsException exception){throw new com.pulseops.exception.BusinessRuleException("Reconfigure a credencial da integração; a chave de criptografia foi alterada");}
    }
}
