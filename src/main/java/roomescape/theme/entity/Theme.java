package roomescape.theme.entity;

import static roomescape.exception.type.RoomescapeExceptionType.EMPTY_DESCRIPTION;
import static roomescape.exception.type.RoomescapeExceptionType.EMPTY_NAME;
import static roomescape.exception.type.RoomescapeExceptionType.EMPTY_THUMBNAIL;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import roomescape.exception.RoomescapeException;

@Entity
public class Theme {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private String thumbnail;

    protected Theme() {

    }

    public Theme(long id, Theme theme) {
        this(id, theme.name, theme.description, theme.thumbnail);
    }

    public Theme(String name, String description, String thumbnail) {
        this(null, name, description, thumbnail);
    }

    public Theme(Long id, String name, String description, String thumbnail) {
        validateName(name);
        validateDescription(description);
        validateThumbnail(thumbnail);
        this.id = id;
        this.name = name;
        this.description = description;
        this.thumbnail = thumbnail;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new RoomescapeException(EMPTY_NAME);
        }
    }

    private void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new RoomescapeException(EMPTY_DESCRIPTION);
        }
    }

    private void validateThumbnail(String thumbnail) {
        if (thumbnail == null || thumbnail.isBlank()) {
            throw new RoomescapeException(EMPTY_THUMBNAIL);
        }
    }

    public boolean isIdOf(long id) {
        return this.id == id;
    }

    public boolean isNameOf(String name) {
        return this.name.equals(name);
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Theme theme = (Theme) o;

        return Objects.equals(id, theme.id);
    }

    @Override
    public String toString() {
        return "Theme{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", thumbnail='" + thumbnail + '\'' +
                '}';
    }
}
