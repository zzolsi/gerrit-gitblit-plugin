/*
 * Copyright 2012 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collection of static methods for retrieving information from a repository.
 * 
 * @author James Moger
 * 
 */
public class CompressionUtils {

	static final Logger LOGGER = LoggerFactory.getLogger(CompressionUtils.class);

	/**
	 * Log an error message and exception.
	 * 
	 * @param t
	 * @param repository
	 *            if repository is not null it MUST be the {0} parameter in the pattern.
	 * @param pattern
	 * @param objects
	 */
	private static void error(Throwable t, Repository repository, String pattern, Object... objects) {
		List<Object> parameters = new ArrayList<Object>();
		if (objects != null && objects.length > 0) {
			for (Object o : objects) {
				parameters.add(o);
			}
		}
		if (repository != null) {
			parameters.add(0, repository.getDirectory().getAbsolutePath());
		}
		LOGGER.error(MessageFormat.format(pattern, parameters.toArray()), t);
	}

	/**
	 * Zips the contents of the tree at the (optionally) specified revision and the (optionally) specified basepath to the supplied outputstream.
	 * 
	 * @param repository
	 * @param basePath
	 *            if unspecified, entire repository is assumed.
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @param os
	 * @return true if repository was successfully zipped to supplied output stream
	 */
	public static boolean zip(Repository repository, String basePath, String objectId, OutputStream os) {
		RevCommit commit = JGitUtils.getCommit(repository, objectId);
		if (commit == null) {
			return false;
		}
		boolean success = false;
		RevWalk rw = new RevWalk(repository);
		TreeWalk tw = new TreeWalk(repository);
		try {
			tw.reset();
			tw.addTree(commit.getTree());
			ZipArchiveOutputStream zos = new ZipArchiveOutputStream(os);
			zos.setComment("Generated by Gitblit");
			if (!StringUtils.isEmpty(basePath)) {
				PathFilter f = PathFilter.create(basePath);
				tw.setFilter(f);
			}
			tw.setRecursive(true);
			MutableObjectId id = new MutableObjectId();
			ObjectReader reader = tw.getObjectReader();
			long modified = commit.getAuthorIdent().getWhen().getTime();
			while (tw.next()) {
				FileMode mode = tw.getFileMode(0);
				if (mode == FileMode.GITLINK || mode == FileMode.TREE) {
					continue;
				}
				tw.getObjectId(id, 0);

				ZipArchiveEntry entry = new ZipArchiveEntry(tw.getPathString());
				entry.setSize(reader.getObjectSize(id, Constants.OBJ_BLOB));
				entry.setComment(commit.getName());
				entry.setUnixMode(mode.getBits());
				entry.setTime(modified);
				zos.putArchiveEntry(entry);

				ObjectLoader ldr = repository.open(id);
				ldr.copyTo(zos);
				zos.closeArchiveEntry();
			}
			zos.finish();
			success = true;
		} catch (IOException e) {
			error(e, repository, "{0} failed to zip files from commit {1}", commit.getName());
		} finally {
			tw.close();
			rw.close();
			rw.dispose();
		}
		return success;
	}

	/**
	 * tar the contents of the tree at the (optionally) specified revision and the (optionally) specified basepath to the supplied outputstream.
	 * 
	 * @param repository
	 * @param basePath
	 *            if unspecified, entire repository is assumed.
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @param os
	 * @return true if repository was successfully zipped to supplied output stream
	 */
	public static boolean tar(Repository repository, String basePath, String objectId, OutputStream os) {
		return tar(null, repository, basePath, objectId, os);
	}

	/**
	 * tar.gz the contents of the tree at the (optionally) specified revision and the (optionally) specified basepath to the supplied outputstream.
	 * 
	 * @param repository
	 * @param basePath
	 *            if unspecified, entire repository is assumed.
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @param os
	 * @return true if repository was successfully zipped to supplied output stream
	 */
	public static boolean gz(Repository repository, String basePath, String objectId, OutputStream os) {
		return tar(CompressorStreamFactory.GZIP, repository, basePath, objectId, os);
	}

	/**
	 * tar.xz the contents of the tree at the (optionally) specified revision and the (optionally) specified basepath to the supplied outputstream.
	 * 
	 * @param repository
	 * @param basePath
	 *            if unspecified, entire repository is assumed.
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @param os
	 * @return true if repository was successfully zipped to supplied output stream
	 */
	public static boolean xz(Repository repository, String basePath, String objectId, OutputStream os) {
		return tar(CompressorStreamFactory.XZ, repository, basePath, objectId, os);
	}

	/**
	 * tar.bzip2 the contents of the tree at the (optionally) specified revision and the (optionally) specified basepath to the supplied outputstream.
	 * 
	 * @param repository
	 * @param basePath
	 *            if unspecified, entire repository is assumed.
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @param os
	 * @return true if repository was successfully zipped to supplied output stream
	 */
	public static boolean bzip2(Repository repository, String basePath, String objectId, OutputStream os) {

		return tar(CompressorStreamFactory.BZIP2, repository, basePath, objectId, os);
	}

	/**
	 * Compresses/archives the contents of the tree at the (optionally) specified revision and the (optionally) specified basepath to the supplied
	 * outputstream.
	 * 
	 * @param algorithm
	 *            compression algorithm for tar (optional)
	 * @param repository
	 * @param basePath
	 *            if unspecified, entire repository is assumed.
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @param os
	 * @return true if repository was successfully zipped to supplied output stream
	 */
	private static boolean tar(String algorithm, Repository repository, String basePath, String objectId, OutputStream os) {
		RevCommit commit = JGitUtils.getCommit(repository, objectId);
		if (commit == null) {
			return false;
		}

		OutputStream cos = os;
		if (!StringUtils.isEmpty(algorithm)) {
			try {
				cos = new CompressorStreamFactory().createCompressorOutputStream(algorithm, os);
			} catch (CompressorException e1) {
				error(e1, repository, "{0} failed to open {1} stream", algorithm);
			}
		}
		boolean success = false;
		RevWalk rw = new RevWalk(repository);
		TreeWalk tw = new TreeWalk(repository);
		try {
			tw.reset();
			tw.addTree(commit.getTree());
			TarArchiveOutputStream tos = new TarArchiveOutputStream(cos);
			tos.setAddPaxHeadersForNonAsciiNames(true);
			tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
			if (!StringUtils.isEmpty(basePath)) {
				PathFilter f = PathFilter.create(basePath);
				tw.setFilter(f);
			}
			tw.setRecursive(true);
			MutableObjectId id = new MutableObjectId();
			long modified = commit.getAuthorIdent().getWhen().getTime();
			while (tw.next()) {
				FileMode mode = tw.getFileMode(0);
				if (mode == FileMode.GITLINK || mode == FileMode.TREE) {
					continue;
				}
				tw.getObjectId(id, 0);

				ObjectLoader loader = repository.open(id);
				if (FileMode.SYMLINK == mode) {
					TarArchiveEntry entry = new TarArchiveEntry(tw.getPathString(), TarArchiveEntry.LF_SYMLINK);
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					loader.copyTo(bos);
					entry.setLinkName(bos.toString());
					entry.setModTime(modified);
					tos.putArchiveEntry(entry);
					tos.closeArchiveEntry();
				} else {
					TarArchiveEntry entry = new TarArchiveEntry(tw.getPathString());
					entry.setMode(mode.getBits());
					entry.setModTime(modified);
					entry.setSize(loader.getSize());
					tos.putArchiveEntry(entry);
					loader.copyTo(tos);
					tos.closeArchiveEntry();
				}
			}
			tos.finish();
			tos.close();
			cos.close();
			success = true;
		} catch (IOException e) {
			error(e, repository, "{0} failed to {1} stream files from commit {2}", algorithm, commit.getName());
		} finally {
			tw.close();
			rw.close();
			rw.dispose();
		}
		return success;
	}
}
