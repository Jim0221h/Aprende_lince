package com.itc.healthtrack.models;

// usuario del sistema: role = student | tutor | admin
// tutorType (solo tutores) = teacher (maestro) | student (alumno que da asesoria y gana credito complementario)
public class User {
    private String uid, email, firstName, lastName, role, tutorType, career, controlNumber, semester;
    public User() {}
    public String getUid() { return uid; }             public void setUid(String v) { uid = v; }
    public String getEmail() { return email; }         public void setEmail(String v) { email = v; }
    public String getFirstName() { return firstName; } public void setFirstName(String v) { firstName = v; }
    public String getLastName() { return lastName; }   public void setLastName(String v) { lastName = v; }
    public String getRole() { return role; }           public void setRole(String v) { role = v; }
    public String getTutorType() { return tutorType; } public void setTutorType(String v) { tutorType = v; }
    public String getCareer() { return career; }       public void setCareer(String v) { career = v; }
    public String getControlNumber() { return controlNumber; } public void setControlNumber(String v) { controlNumber = v; }
    public String getSemester() { return semester; }   public void setSemester(String v) { semester = v; }
    @Override public String toString() { return firstName + " " + lastName; }
}