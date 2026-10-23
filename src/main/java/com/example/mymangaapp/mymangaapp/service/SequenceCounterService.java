package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.entity.SequenceCounter;
import com.example.mymangaapp.mymangaapp.repository.SequenceCounterRepository;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SequenceCounterService {

    SequenceCounterRepository sequenceCounterRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String name) {
        SequenceCounter sequenceCounter = sequenceCounterRepository
                .findForUpdate(name)
                .orElseThrow(() -> new AppException(ResponseCode.SEQUENCE_COUNTER_NOT_FOUND));

        long value = sequenceCounter.getNextValue();
        log.info("Value: {}", value);

        sequenceCounter.setNextValue(value + 1);

        return value;
    }

}
