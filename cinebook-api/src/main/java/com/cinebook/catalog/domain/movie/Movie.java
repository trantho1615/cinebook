package com.cinebook.catalog.domain.movie;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "movies")
public class Movie {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "original_title", length = 255)
    private String originalTitle;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "movie_genres", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "genre", nullable = false, length = 50)
    private Set<String> genres = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "age_rating", nullable = false, length = 10)
    private AgeRating ageRating;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "trailer_url", length = 500)
    private String trailerUrl;

    @Column(name = "release_date", nullable = false)
    private LocalDate releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovieStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Movie() {
        // JPA yeu cau constructor khong tham so
    }

    public static Movie create(String title, String originalTitle, String description,
                               int durationMin, Set<String> genres, AgeRating ageRating,
                               String posterUrl, String trailerUrl, LocalDate releaseDate) {
        Movie movie = new Movie();
        Instant now = Instant.now();
        movie.id = UUID.randomUUID();
        movie.title = title;
        movie.originalTitle = originalTitle;
        movie.description = description;
        movie.durationMin = durationMin;
        movie.genres = new LinkedHashSet<>(genres);
        movie.ageRating = ageRating;
        movie.posterUrl = posterUrl;
        movie.trailerUrl = trailerUrl;
        movie.releaseDate = releaseDate;
        // Phim moi tao luon o trang thai sap chieu; chuyen sang dang chieu la mot
        // hanh dong rieng cua admin, khong phai hieu ung phu cua viec tao.
        movie.status = MovieStatus.COMING_SOON;
        movie.createdAt = now;
        movie.updatedAt = now;
        return movie;
    }

    public void changeStatus(MovieStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationMin() {
        return durationMin;
    }

    /**
     * Tra ban sao bat bien: tra thang Set noi bo thi ben ngoai sua duoc trang thai
     * entity ma khong di qua phuong thuc nao cua no.
     */
    public Set<String> getGenres() {
        return Set.copyOf(genres);
    }

    public AgeRating getAgeRating() {
        return ageRating;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public String getTrailerUrl() {
        return trailerUrl;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public MovieStatus getStatus() {
        return status;
    }
}
