package org.grakovne.lissen.content.cache.persistent

import org.grakovne.lissen.content.cache.common.findRelatedFiles
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter

/**
 * Files backing [droppingChapters], minus any file a chapter in [keepingChapters] still needs -
 * a file can span more than one chapter (see [findRelatedFiles]), so dropping a chapter must never
 * delete audio a surviving neighbor still relies on.
 */
fun calculateFilesToDrop(
  book: DetailedItem,
  droppingChapters: List<PlayingChapter>,
  keepingChapters: List<PlayingChapter>,
): List<BookFile> {
  val droppingFiles = droppingChapters.flatMap { findRelatedFiles(it, book.files) }.distinctBy { it.id }
  val keepingFileIds = keepingChapters.flatMap { findRelatedFiles(it, book.files) }.map { it.id }.toSet()
  return droppingFiles.filterNot { it.id in keepingFileIds }
}
