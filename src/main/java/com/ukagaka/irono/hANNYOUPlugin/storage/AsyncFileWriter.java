package com.ukagaka.irono.hANNYOUPlugin.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ファイルへの書き込みを専用スレッド1本で行うため、TPSを下げない。
 * 書き込み待ちの間に内容が重なった場合は、最新の内容だけを書き込む。
 * 一時ファイルに書いてから置き換えるので、書き込み中に落ちてもファイルが壊れない。
 */
public final class AsyncFileWriter {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Logger logger;
    private final Path file;
    private final ExecutorService executor;
    /** 書き込み待ちの内容（なければnull） */
    private final AtomicReference<String> pending = new AtomicReference<>();

    public AsyncFileWriter(Path file, String threadName, Logger logger) {
        this.logger = logger;
        this.file = file;
        this.executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name(threadName).daemon().factory());
    }

    /** 内容を書き込む（呼び出し元はすぐ戻る） */
    public void write(String content) {
        if (pending.getAndSet(content) == null && !executor.isShutdown()) {
            executor.execute(this::flush);
        }
    }

    /** 読み込めなかったファイルを {@code <名前>.broken-<日時>} に退避する */
    public void backupBroken(Exception cause, String fallback) {
        String name = file.getFileName().toString();
        Path backup = file.resolveSibling(name + ".broken-" + TIMESTAMP.format(LocalDateTime.now()));
        try {
            Files.copy(file, backup);
            logger.log(Level.SEVERE, name + " を読み込めませんでした。" + backup.getFileName()
                    + " に退避し、" + fallback, cause);
        } catch (IOException copyError) {
            logger.log(Level.SEVERE, name + " を読み込めず、退避もできませんでした", copyError);
        }
    }

    private void flush() {
        String content = pending.getAndSet(null);
        if (content == null) return;
        String name = file.getFileName().toString();
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(name + ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, name + " への保存に失敗しました", e);
        }
    }

    /** 未処理の書き込みをすべて終える */
    public void close() {
        executor.shutdown();
        try {
            if (executor.awaitTermination(10, TimeUnit.SECONDS)) {
                flush(); // 停止の直前に積まれた分
            } else {
                logger.warning(file.getFileName() + " への書き込みが時間内に終わりませんでした");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
