package me.coley.recaf.workspace.resource;

import me.coley.recaf.workspace.resource.source.ContentSource;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Workspace unit.
 *
 * @author Matt Coley
 */
public class Resource {
	private final ClassMap classes = new ClassMap(this);
	private final FileMap files = new FileMap(this);
	private final ContentSource contentSource;

	/**
	 * Create the resource from the given content source.
	 *
	 * @param contentSource
	 * 		Source of content, containing classes and files.
	 */
	public Resource(ContentSource contentSource) {
		this.contentSource = contentSource;
	}

	/**
	 * Clears any existing data and populates the resource from the {@link #getContentSource() content source}.
	 *
	 * @throws IOException
	 * 		When the {@link #getContentSource() content source} cannot be read from.
	 */
	public void read() throws IOException {
		// Reset
		classes.clear();
		files.clear();
		// Read
		contentSource.readInto(this);
	}

	/**
	 * @param path
	 * 		Path to write the contents of the resource to.
	 *
	 * @throws IOException
	 * 		When the {@link #getContentSource() content source} write handler fails to write to the given path.
	 */
	public void write(Path path) throws IOException {
		contentSource.writeTo(this, path);
	}

	/**
	 * @return Collection of the classes contained by the resource.
	 */
	public ClassMap getClasses() {
		return classes;
	}

	/**
	 * @return Collection of the files contained by the resource.
	 */
	public FileMap getFiles() {
		return files;
	}

	/**
	 * The content source of a resource contains information about where the content of the resource was loaded from.
	 * For example, a jar,war,zip,url,etc.
	 * <br>
	 * Internally it will provide access to loading from the content.
	 *
	 * @return Content location information.
	 */
	public ContentSource getContentSource() {
		return contentSource;
	}

	/**
	 * @param classListener
	 * 		Resource listener for class updates.
	 */
	public void setClassListener(ResourceItemListener<ClassInfo> classListener) {
		classes.setListener(classListener);
	}

	/**
	 * @param fileListener
	 * 		Resource listener for file updates.
	 */
	public void setFileListener(ResourceItemListener<FileInfo> fileListener) {
		files.setListener(fileListener);
	}
}