package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.io.ArchiveDestination;
import in.bushansirgur.billingsoftware.io.BackupFileInfo;

import java.nio.file.Path;
import java.util.List;

public interface BackupService {

    /**
     * Create a full PostgreSQL dump (.sql.gz). Does not delete any database data.
     * @return local path or s3:// URI
     */
    String createBackup(ArchiveDestination destination);

    List<BackupFileInfo> listLocalBackups();

    Path resolveLocalBackup(String filename);

    void pruneOldLocalBackups();
}
