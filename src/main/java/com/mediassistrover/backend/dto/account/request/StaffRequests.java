package com.mediassistrover.backend.dto.account.request;

import jakarta.validation.constraints.*;

public final class StaffRequests {
    private StaffRequests() {}
    public record Create(
            @NotBlank @Size(min=4,max=100) @Pattern(regexp="^[a-zA-Z0-9._-]+$") String username,
            @NotBlank @Size(min=8,max=72) String temporaryPassword,
            @NotBlank @Size(max=160) String fullName,
            @NotBlank @Email @Size(max=254) String email,
            @Size(max=32) @Pattern(regexp="^\\+?[0-9][0-9 .-]{7,30}$") String phone,
            @NotBlank @Size(max=64) String staffCode,
            @Size(max=64) String departmentCode,
            @Size(max=100) String jobTitle,
            @Size(max=20) String contactExtension,
            @Size(max=2048) String url) {}
    /** Omitted avatar keeps the current image; an explicit null still clears it. */
    public static final class Update {
        @NotBlank @Size(max=160) private final String fullName;
        @NotBlank @Email @Size(max=254) private final String email;
        @Size(max=32) @Pattern(regexp="^\\+?[0-9][0-9 .-]{7,30}$") private final String phone;
        @NotBlank @Size(max=64) private final String staffCode;
        @Size(max=64) private final String departmentCode;
        @Size(max=100) private final String jobTitle;
        @Size(max=20) private final String contactExtension;
        @Size(max=2048) private String url;
        @com.fasterxml.jackson.annotation.JsonIgnore private boolean urlProvided;

        @com.fasterxml.jackson.annotation.JsonCreator
        public Update(@com.fasterxml.jackson.annotation.JsonProperty("fullName") String fullName,
                @com.fasterxml.jackson.annotation.JsonProperty("email") String email,
                @com.fasterxml.jackson.annotation.JsonProperty("phone") String phone,
                @com.fasterxml.jackson.annotation.JsonProperty("staffCode") String staffCode,
                @com.fasterxml.jackson.annotation.JsonProperty("departmentCode") String departmentCode,
                @com.fasterxml.jackson.annotation.JsonProperty("jobTitle") String jobTitle,
                @com.fasterxml.jackson.annotation.JsonProperty("contactExtension") String contactExtension) {
            this.fullName=fullName;this.email=email;this.phone=phone;this.staffCode=staffCode;
            this.departmentCode=departmentCode;this.jobTitle=jobTitle;this.contactExtension=contactExtension;
        }
        public Update(String fullName,String email,String phone,String staffCode,String departmentCode,
                String jobTitle,String contactExtension,String url) {
            this(fullName,email,phone,staffCode,departmentCode,jobTitle,contactExtension);setUrl(url);
        }
        @com.fasterxml.jackson.annotation.JsonSetter(value="url",nulls=com.fasterxml.jackson.annotation.Nulls.SET)
        public void setUrl(String value){url=value;urlProvided=true;}
        public String fullName(){return fullName;} public String email(){return email;} public String phone(){return phone;}
        public String staffCode(){return staffCode;} public String departmentCode(){return departmentCode;}
        public String jobTitle(){return jobTitle;} public String contactExtension(){return contactExtension;}
        public String url(){return url;}
        @com.fasterxml.jackson.annotation.JsonIgnore public boolean hasUrl(){return urlProvided;}
    }
}
