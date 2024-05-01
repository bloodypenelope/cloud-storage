package com.example.cloudstorage.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.List;

import com.example.cloudstorage.exceptions.StorageException;
import com.example.cloudstorage.exceptions.StorageFileNotFoundException;
import com.example.cloudstorage.exceptions.StorageInvalidRequestException;
import com.example.cloudstorage.properties.StorageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@SuppressWarnings("unused")
public class FileSystemStorageService implements StorageService {
    private final Path root;

    @Autowired
    public FileSystemStorageService(StorageProperties properties) {
        if (properties.getLocation().trim().isEmpty())
            throw new StorageException("Storage system's location cannot be empty");
        this.root = Paths.get(properties.getLocation());
    }

    @Override
    public void init() {
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new StorageException("Could not initialize the storage", e);
        }
    }

    @Override
    public Path load(Path path) {
        Path file = this.root.resolve(path);
        System.out.println(file);
        System.out.println(this.root);
        if (!Files.exists(file))
            throw new StorageFileNotFoundException("Could not find a file");

        return file;
    }

    @Override
    public List<Path> loadDirectory(Path path) {
        Path parent = this.load(path);
        if (!Files.isDirectory(parent))
            throw new StorageInvalidRequestException("Trying to load files from non-directory");

        try (Stream<Path> files = Files.list(parent)) {
            return files.collect(Collectors.toList());
        } catch (IOException e) {
            throw new StorageException("Failed to read stored files");
        }
    }

    @Override
    public Resource loadAsResource(Path path) {
        Path file = this.load(path);
        if (Files.isDirectory(file))
            throw new StorageInvalidRequestException("Cannot load a directory as a resource");

        try {
            return new UrlResource(file.toUri());
        } catch (MalformedURLException e) {
            throw new StorageException("Malformed path to the file");
        }
    }

    @Override
    public Path uploadFile(Path path, MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new StorageInvalidRequestException("Failed to store empty file");

        Path parent = this.load(path);
        Path destination = parent.resolve(Objects.requireNonNull(file.getOriginalFilename()));
        if (!Files.isDirectory(parent))
            throw new StorageInvalidRequestException("Trying to load a file in a non-directory");

        try {
            InputStream inputStream = file.getInputStream();
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException e) {
            throw new StorageException("Failed to store file", e);
        }
    }

    @Override
    public Path createDirectory(Path path, String name) {
        name = name.replaceAll(".*?\\.+$", "");
        Path parent = this.load(path);
        if (!Files.isDirectory(parent))
            throw new StorageInvalidRequestException("Unable to create a directory inside of a regular file");

        try {
            Path destination = parent.resolve(name);
            System.out.println(destination);
            Files.createDirectory(destination);
            return destination;
        } catch (IOException e) {
            throw new StorageInvalidRequestException("Invalid name for a directory");
        }
    }

    @Override
    public Path move(Path path, String newDirectory) {
        Path file = this.load(path);
        Path parent = file.getParent();
        Path target = newDirectory.equals(".") ? parent.getParent() : parent.resolve(newDirectory);
        if (!Files.exists(target) || !Files.isDirectory(target) || target.equals(this.root))
            throw new StorageInvalidRequestException("Invalid target folder");

        try {
            Path newFile = target.resolve(file.getFileName());
            Files.move(file, newFile, StandardCopyOption.REPLACE_EXISTING);
            return newFile;
        } catch (IOException e) {
            throw new StorageException("Could not move a file/directory");
        }
    }

    @Override
    public Path rename(Path path, String newName) {
        Path file = this.load(path);
        Path parent = file.getParent();
        newName = newName.replaceAll(".*?\\.+$", "");

        try {
            Path newFile = parent.resolve(newName);
            Files.move(file, newFile);
            return newFile;
        } catch (IOException e) {
            throw new StorageInvalidRequestException("Invalid name for a file/directory");
        }
    }

    @Override
    public void delete(Path path) {
        Path file = this.load(path);

        try {
            FileSystemUtils.deleteRecursively(file);
        } catch (IOException e) {
            throw new StorageException("Could not delete a file/directory");
        }
    }
}
