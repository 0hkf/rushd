package com.rushd.images;

import com.rushd.entity.*;
import com.rushd.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ImageCommitFailureTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void commitFailureCleansOnlyKnownUnreferencedFilesAndRetainsAmbiguousOutcomes(boolean ambiguous, @TempDir Path temporary) throws Exception {
        var properties=mock(PropertyRepository.class);var users=mock(UserRepository.class);var images=mock(PropertyImages.class);
        var collections=mock(ImageCollections.class);var manager=mock(PlatformTransactionManager.class);
        var storage=new PrivateImageStorage(true,temporary.toRealPath().toString());
        var admin=new User();admin.setId(10L);admin.setRole(Role.ADMIN);admin.setEmail("p10@example.invalid");
        when(users.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(properties.findByIdForUpdate(1L)).thenReturn(Optional.of(new Property()));
        when(collections.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        when(images.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        if(ambiguous)when(images.findByStorageKey(any())).thenThrow(new DataSourceUnavailable());
        doAnswer(invocation->{
            try(var files=Files.list(temporary)){assertEquals(2,files.filter(p->p.toString().endsWith(".jpg")).count());}
            throw new TransactionSystemException("synthetic commit failure");
        }).when(manager).commit(any());
        var service=new PropertyImageService(properties,users,images,collections,storage,new ImageProcessor(),manager);
        try(var lease=storage.reserveUpload()) {
            assertThrows(TransactionSystemException.class,()->service.upload(1L,UUID.randomUUID(),ImageFixtures.image("jpeg",640,480,false),"a.jpg","image/jpeg",true,PropertyImage.RightsBasis.OWNED,null,null,admin.getEmail()));
            try(var files=Files.list(temporary)){assertEquals(ambiguous?2:0,files.filter(p->p.toString().endsWith(".jpg")).count());}
        } finally {storage.close();}
    }
    @Test void disabledPrimaryLookupNeverQueriesImageMetadata() {
        var images=mock(PropertyImages.class);var collections=mock(ImageCollections.class);
        var service=new PropertyImageService(mock(PropertyRepository.class),mock(UserRepository.class),images,collections,
                new PrivateImageStorage(false,""),new ImageProcessor(),mock(PlatformTransactionManager.class));
        assertTrue(service.primaries(List.of(1L)).isEmpty());verifyNoInteractions(images,collections);
    }
    private static class DataSourceUnavailable extends RuntimeException {}
}
