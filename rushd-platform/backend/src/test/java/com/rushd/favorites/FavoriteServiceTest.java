package com.rushd.favorites;

import com.rushd.entity.*;
import com.rushd.images.PrimaryImageLookup;
import com.rushd.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FavoriteServiceTest {
    final Favorites favorites=mock(Favorites.class);
    final UserRepository users=mock(UserRepository.class);
    final PropertyRepository properties=mock(PropertyRepository.class);
    final PrimaryImageLookup images=mock(PrimaryImageLookup.class);
    final FavoriteService service=new FavoriteService(favorites,users,properties,images,true);
    Favorite row(long id, PropertyStatus status) {
        var p=new Property();p.setId(id);p.setTitle("Synthetic");p.setStatus(status);p.setType(PropertyType.LAND);
        p.setCity("مكة");p.setDistrict("النوارية");p.setPrice(new BigDecimal("9007199254740993.01"));p.setArea(new BigDecimal("500.25"));
        var row=new Favorite(1L,id,Instant.now());row.property=p;return row;
    }
    void page(List<Favorite> rows) {
        var user=new User();user.setId(1L);when(users.findByEmail("buyer@example.invalid")).thenReturn(Optional.of(user));
        when(favorites.pageFor(eq(1L),any())).thenReturn(new PageImpl<>(rows));
    }
    @Test void oneBulkImageLookupContainsOnlyPublicRowsFromTheCurrentPage() {
        page(List.of(row(15,PropertyStatus.ACTIVE),row(16,PropertyStatus.DRAFT),row(17,PropertyStatus.SOLD)));
        when(images.primaries(List.of(15L,17L))).thenReturn(Map.of());
        var result=service.list("buyer@example.invalid",0,12);
        verify(images,times(1)).primaries(List.of(15L,17L));verifyNoMoreInteractions(images);
        assertNull(result.getContent().get(1).property());
    }
    @Test void allHiddenPageDoesNotRequestImageMetadata() {
        page(List.of(row(16,PropertyStatus.INACTIVE)));assertFalse(service.list("buyer@example.invalid",0,12).getContent().get(0).available());
        verifyNoInteractions(images);
    }
    @Test void disabledGuardPrecedesAllRepositoryAndImageCalls() {
        var disabled=new FavoriteService(favorites,users,properties,images,false);
        assertEquals(503,assertThrows(FavoriteFailure.class,()->disabled.list("buyer@example.invalid",0,12)).status());
        assertThrows(FavoriteFailure.class,()->disabled.status("buyer@example.invalid",List.of("15")));
        assertThrows(FavoriteFailure.class,()->disabled.add("buyer@example.invalid","15"));
        assertThrows(FavoriteFailure.class,()->disabled.remove("buyer@example.invalid","15"));
        verifyNoInteractions(favorites,users,properties,images);
    }
}
