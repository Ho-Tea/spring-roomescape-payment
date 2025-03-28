package roomescape.member.domain;

import roomescape.auth.domain.Role;

public class LoginMember {
    private final Long id;
    private final String name;
    private final Role role;

    public LoginMember(Long id, String name, Role role) {
        this.id = id;
        this.name = name;
        this.role = role;
    }

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "LoginMember{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", role=" + role +
                '}';
    }
}
