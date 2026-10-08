package ru.sfu.student.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Populated from cached lessons during repository initialization, preserving all old task data.
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `lessonBindingKey` TEXT")
        }
    }
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `groups` (`groupId` INTEGER NOT NULL, `groupName` TEXT NOT NULL, `course` INTEGER NOT NULL, `educationLevel` TEXT NOT NULL, `isSession` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`groupId`))")
            db.execSQL("INSERT INTO `groups` VALUES (107691, 'КИ25-20Б (2 подгруппа)', 2, 'bachelor', 0, COALESCE((SELECT lastImportAt FROM settings WHERE id = 1), 0))")
            db.execSQL("CREATE TABLE `lessons_new` (`groupId` INTEGER NOT NULL, `id` INTEGER NOT NULL, `data_id` INTEGER NOT NULL, `data_week` INTEGER NOT NULL, `data_dayOfWeek` INTEGER NOT NULL, `data_startTime` TEXT NOT NULL, `data_endTime` TEXT NOT NULL, `data_subject` TEXT NOT NULL, `data_teacher` TEXT NOT NULL, `data_type` TEXT NOT NULL, `data_building` TEXT NOT NULL, `data_room` TEXT NOT NULL, `data_description` TEXT NOT NULL, `data_subjectId` INTEGER, PRIMARY KEY(`groupId`, `id`), FOREIGN KEY(`groupId`) REFERENCES `groups`(`groupId`) ON UPDATE CASCADE ON DELETE CASCADE)")
            db.execSQL("INSERT INTO lessons_new SELECT 107691, id, data_id, data_week, data_dayOfWeek, data_startTime, data_endTime, data_subject, data_teacher, data_type, data_building, data_room, data_description, NULL FROM lessons")
            db.execSQL("DROP TABLE lessons")
            db.execSQL("ALTER TABLE lessons_new RENAME TO lessons")
            db.execSQL("CREATE TABLE `tasks_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `subject` TEXT NOT NULL, `notes` TEXT NOT NULL, `dueAt` INTEGER NOT NULL, `remindMinutes` INTEGER NOT NULL, `done` INTEGER NOT NULL, `groupId` INTEGER, `subjectId` INTEGER, FOREIGN KEY(`groupId`) REFERENCES `groups`(`groupId`) ON UPDATE CASCADE ON DELETE SET NULL)")
            db.execSQL("INSERT INTO tasks_new SELECT id, title, subject, notes, dueAt, remindMinutes, done, 107691, NULL FROM tasks")
            db.execSQL("DROP TABLE tasks")
            db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
            db.execSQL("CREATE INDEX index_tasks_groupId ON tasks(groupId)")
            db.execSQL("CREATE TABLE `settings_new` (`id` INTEGER NOT NULL, `deadlineNotifications` INTEGER NOT NULL, `lessonNotifications` INTEGER NOT NULL, `lessonLeadMinutes` INTEGER NOT NULL, `anchorMonday` TEXT NOT NULL, `anchorWeek` INTEGER NOT NULL, `weekConfirmed` INTEGER NOT NULL, `lastImportAt` INTEGER NOT NULL, `activeGroupId` INTEGER, `primaryGroupId` INTEGER, PRIMARY KEY(`id`))")
            db.execSQL("INSERT INTO settings_new SELECT id, deadlineNotifications, lessonNotifications, CASE WHEN lessonLeadMinutes IN (15,30,60) THEN lessonLeadMinutes ELSE 15 END, anchorMonday, anchorWeek, weekConfirmed, lastImportAt, 107691, 107691 FROM settings")
            db.execSQL("DROP TABLE settings")
            db.execSQL("ALTER TABLE settings_new RENAME TO settings")
        }
    }
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `groups` ADD COLUMN `universityCode` TEXT NOT NULL DEFAULT 'sfu'")
            db.execSQL("ALTER TABLE `groups` ADD COLUMN `weekAnchorMonday` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `groups` ADD COLUMN `weekAnchorWeek` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `groups` ADD COLUMN `weekConfirmed` INTEGER NOT NULL DEFAULT 0")
            // Existing groups are all SFU: preserve their previously selected week cycle.
            db.execSQL("UPDATE `groups` SET `weekAnchorMonday` = COALESCE((SELECT anchorMonday FROM settings WHERE id = 1), ''), `weekAnchorWeek` = COALESCE((SELECT anchorWeek FROM settings WHERE id = 1), 1), `weekConfirmed` = COALESCE((SELECT weekConfirmed FROM settings WHERE id = 1), 0)")
        }
    }
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `groups` ADD COLUMN `selectedSubgroup` INTEGER")
            db.execSQL("ALTER TABLE `lessons` ADD COLUMN `data_subgroup` INTEGER")
            // Preserve cached IRNITU subgroup markers from version 3 without requiring a download.
            db.execSQL("UPDATE `lessons` SET `data_subgroup` = CAST(substr(`data_description`, 11) AS INTEGER) WHERE `groupId` IN (SELECT `groupId` FROM `groups` WHERE `universityCode` = 'irnitu') AND `data_description` GLOB 'Подгруппа [1-9]*'")
        }
    }
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Old tasks only recorded their subject and deadline; do not guess the chosen class.
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `lessonId` INTEGER")
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `lessonDate` TEXT")
        }
    }
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // NULL keeps the legacy single reminder; an empty list explicitly means no reminders.
            db.execSQL("ALTER TABLE `tasks` ADD COLUMN `reminderMinutes` TEXT")
            db.execSQL("INSERT OR IGNORE INTO deliveries (`key`, sentAt) SELECT d.`key` || ':' || t.remindMinutes, d.sentAt FROM deliveries d JOIN tasks t ON d.`key` = 'task:' || t.id || ':' || t.dueAt WHERE t.remindMinutes >= 0")
        }
    }
}
