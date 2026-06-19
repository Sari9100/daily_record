package com.familyos.storage;

import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 외장 SSD(로컬 파일시스템) 기반 StorageService 구현.
 *
 * <p>storageKey 는 {@code family_{id}/yyyy/MM/uuid.ext}. 경로 탈출(`..`) 차단 + 루트 하위 강제.
 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
public class LocalSsdStorage implements StorageService {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);
    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9_./-]+$");

    private final Path root;

    public LocalSsdStorage(StorageProperties props) {
        this.root = Path.of(props.localRoot()).toAbsolutePath().normalize();
    }

    @Override
    public String generateKey(Long familyId, String originalFilename, @Nullable Instant takenAt) {
        Instant when = takenAt == null ? Instant.now() : takenAt;
        String ext = extensionOf(originalFilename);
        return "family_%d/%s/%s/%s%s".formatted(
                familyId, YEAR.format(when), MONTH.format(when), UUID.randomUUID(), ext);
    }

    @Override
    public void store(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new BusinessException("파일 저장에 실패했습니다.");
        }
    }

    @Override
    public byte[] load(String storageKey) {
        Path target = resolve(storageKey);
        if (!Files.exists(target)) {
            throw new NotFoundException("파일을 찾을 수 없습니다.");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new BusinessException("파일 읽기에 실패했습니다.");
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    /** 경로 탈출 차단 + 루트 하위 강제. */
    private Path resolve(String storageKey) {
        if (storageKey == null || !SAFE_KEY.matcher(storageKey).matches() || storageKey.contains("..")) {
            throw new BusinessException("잘못된 저장 키입니다.");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException("잘못된 저장 키입니다.");
        }
        return resolved;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,5}") ? ext : "";
    }
}
