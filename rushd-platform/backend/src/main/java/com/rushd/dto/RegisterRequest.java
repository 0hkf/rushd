package com.rushd.dto;

import com.rushd.entity.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "الاسم مطلوب")
    @Size(max = 255, message = "الاسم يجب ألا يتجاوز 255 حرفًا")
    private String name;

    @NotBlank(message = "البريد الإلكتروني مطلوب")
    @Email(message = "صيغة البريد الإلكتروني غير صحيحة")
    @Size(max = 255, message = "البريد الإلكتروني يجب ألا يتجاوز 255 حرفًا")
    @com.rushd.validation.FitsNormalizedEmailColumn
    private String email;

    @NotBlank(message = "كلمة المرور مطلوبة")
    @Size(min = 6, message = "كلمة المرور يجب أن تكون 6 أحرف على الأقل")
    @com.rushd.validation.ValidPasswordByteLength
    private String password;

    @NotNull(message = "الدور مطلوب")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.rushd.dto.deserialization.StrictStringEnumDeserializer.class)
    private Role role;

    @JsonIgnore
    @AssertTrue(message = "التسجيل العام متاح للمستخدم فقط")
    public boolean isPublicRoleValid() {
        return role == null || role == Role.BUYER;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
