package com.ulake.api.controllers;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.acls.domain.BasePermission;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Sid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.Assert;
import org.springframework.validation.BindingResult;

import com.ulake.api.models.File;
import com.ulake.api.models.Group;
import com.ulake.api.models.User;
import com.ulake.api.payload.response.MessageResponse;
import com.ulake.api.repository.FileRepository;
import com.ulake.api.repository.GroupRepository;
import com.ulake.api.repository.UserRepository;
import com.ulake.api.security.services.FilesStorageService;
import com.ulake.api.security.services.LocalPermissionService;
import com.ulake.api.security.services.impl.UserDetailsImpl;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api")
public class FileController {
    @Autowired
    private UserRepository userRepository;

    @Autowired
	private FileRepository fileRepository;
    
    @Autowired
	private GroupRepository groupRepository;
    
    @Autowired
    private LocalPermissionService permissionService;
    
    @Autowired
    FilesStorageService storageService;
    private Logger LOGGER = LoggerFactory.getLogger(FileController.class);

	@Operation(summary = "Add a file", description = "This can only be done by logged in user having the file permissions.", 
			security = { @SecurityRequirement(name = "bearer-key") },
			tags = { "file" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Status OK")
			})
	@PostMapping(value = "/files", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
	@PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostAuthorize("hasPermission(returnObject, 'READ')")
	public ResponseEntity<MessageResponse> uploadFile(@RequestParam("file") MultipartFile file) {
	    String message = "";
	    try {
	      storageService.save(file);
	      message = "Uploaded the file successfully: " + file.getOriginalFilename();
	      return ResponseEntity.status(HttpStatus.OK).body(new MessageResponse(message));
	    } catch (Exception e) {
	      message = "Could not upload the file: " + file.getOriginalFilename() + "!";
	      return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(new MessageResponse(message));
	    }
	}	  
		
	@Operation(summary = "Update a file by ID", description = "This can only be done by admin.", 
			security = { @SecurityRequirement(name = "bearer-key") },
			tags = { "file" })
	@ApiResponses(value = @ApiResponse(description = "successful operation"))
	@PutMapping("/files/{id}")
	@PreAuthorize("hasRole('ADMIN') or hasRole('USER') or hasPermission(#file, 'WRITE')")
	public File updateFile(@PathVariable("id") Long id, @RequestBody File file) {
	  Optional<File> fileData = fileRepository.findById(id);
//	  TODO Will fail if not found
	  File _file = fileData.get();	    	
	  _file.setName(file.getName());
	  _file.setCid(file.getCid());
	  _file.setMimeType(file.getMimeType());
	  _file.setSource(file.getSource());
	  _file.setTopics(file.getTopics());
	  _file.setSize(file.getSize());
      return fileRepository.save(_file);
	}
	
	@Operation(summary = "Get a file by ID", description = "This can only be done by logged in user.", 
			security = { @SecurityRequirement(name = "bearer-key") },
			tags = { "file" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(schema = @Schema(implementation = File.class))),
			@ApiResponse(responseCode = "400", description = "Invalid ID supplied", content = @Content),
			@ApiResponse(responseCode = "404", description = "File not found", content = @Content) })
	@GetMapping("/files/{id}")
    @PreAuthorize("(hasRole('ADMIN')) or (hasPermission(#id, 'com.ulake.api.models.File', 'READ'))")
	public File getFileById(@PathVariable("id") Long id) {
	  Optional<File> fileData = fileRepository.findById(id);
	  File _file = fileData.get();
	  return _file;
	}
	
	@Operation(summary = "Delete a file by ID", description = "This can only be done by logged in user.", 
			security = { @SecurityRequirement(name = "bearer-key") },
			tags = { "file" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(schema = @Schema(implementation = File.class))),
			@ApiResponse(responseCode = "400", description = "Invalid ID supplied", content = @Content),
			@ApiResponse(responseCode = "404", description = "File not found", content = @Content) })
	@DeleteMapping("/files/{id}")
	@PreAuthorize("hasRole('ADMIN') or hasRole('USER') or hasPermission(#file, 'WRITE')")
	public ResponseEntity<File> deleteFileById(@PathVariable("id") long id){
		try {
			Optional<File> fileData = fileRepository.findById(id);
			File file = fileData.get();
			fileRepository.deleteById(id);
			permissionService.removeAcl(file);	
			return new ResponseEntity<>(HttpStatus.OK);
		} 
		catch (Exception e) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@Operation(summary = "Get all files", description = "This can only be done by logged in user with file permissions.", 
			security = { @SecurityRequirement(name = "bearer-key") },
			tags = { "file" })
	@ApiResponses(value = @ApiResponse(description = "successful operation"))
	@GetMapping("/files/all")
	@PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
	public List<File> getAllFiles(@RequestParam(required=false) String name){
		List<File> files = new ArrayList<File>();
		if (name == null)
			fileRepository.findAll().forEach(files::add);
		else
			fileRepository.findByNameContaining(name).forEach(files::add);
		return files;
	}
	
	@Operation(summary = "Get File Data", description = "This can only be done by logged in user and those who have read permssions of file.", 
			security = { @SecurityRequirement(name = "bearer-key") },
			tags = { "file" })
	@PreAuthorize("hasRole('ADMIN') or hasRole('USER') or hasPermission(#file, 'READ')")
	@GetMapping("/files/data/{id}")
	public ResponseEntity<byte[]> getFileData(@PathVariable Long id) {
	    File fileInfo = fileRepository.findById(id).get();

	    return ResponseEntity.ok()
	        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileInfo.getName() + "\"")
	        .body(fileInfo.getData());
	}
	
}
