package com.example.easystay.service.concretes;

import com.example.easystay.core.exceptionhandling.exception.types.BusinessException;
import com.example.easystay.core.mail.EmailService;
import com.example.easystay.mapper.ReservationMapper;
import com.example.easystay.model.entity.Reservation;
import com.example.easystay.model.entity.Room;
import com.example.easystay.model.entity.User;
import com.example.easystay.model.enums.ReservationStatus;
import com.example.easystay.model.enums.Status;
import com.example.easystay.repository.ReservationRepository;
import com.example.easystay.repository.RoomRepository;
import com.example.easystay.repository.UserRepository;
import com.example.easystay.service.abstracts.ReservationService;
import com.example.easystay.service.dtos.requests.reservation.AddReservationRequest;
import com.example.easystay.service.dtos.requests.reservation.UpdateReservationRequest;
import com.example.easystay.service.dtos.responses.reservation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class ReservationServiceImpl implements ReservationService {
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final EmailService emailService;

    @Override
    public List<ListReservationResponse> getAll() {
        List<Reservation> reservationList = reservationRepository.findAll();
      return  reservationList.stream().map(r -> new ListReservationResponse(r.getTotalPrice())).toList();
    }

    @Override
    public AddReservationResponse add(AddReservationRequest request) {
        User user = userRepository.findById(request.getUserId()).orElseThrow(()
                -> new BusinessException("Böyle bir Id'ye sahip kullanıcı bulunamamıştır."));
        Room room = roomRepository.findById(request.getRoomId()).orElseThrow(()
                -> new BusinessException("Böyle bir Id'ye sahip oda bulunamamıştır."));
        Reservation reservation = ReservationMapper.INSTANCE.reservationFromRequest(request);
        isRoomFull(room);
        reservation.setReservationStatus(ReservationStatus.PENDING);
        reservation.setUser(user);
        reservation.setRoom(room);
        room.setStatus(Status.OCCUPIED);
        reservation = reservationRepository.save(reservation);
        AddReservationResponse response = ReservationMapper.INSTANCE.reservationFromResponse(reservation);
        return response;
    }

    @Override
    public List<ListMyReservationResponse> getUserReservations() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName(); //tokendan kullanıcı adını al
        User user = userRepository.findByEmail(username).orElseThrow();
        Long userId = user.getId();
        List<Reservation> reservationList = reservationRepository.findByUserId(userId).stream().toList();
        return   reservationList.stream().filter(r -> r.getReservationStatus()==ReservationStatus.APPROVED).map(r -> new ListMyReservationResponse(r.getTotalPrice())).toList();

    }

    @Override
    public AddReservationResponse update(UpdateReservationRequest request) {
        Reservation reservation = reservationRepository.findById(request.getId()).orElseThrow(()-> new BusinessException("Böyle bir rezervasyon bulunamamıştır."));
        reservation.setReservationStatus(request.getStatus());
        if(request.getStatus()==ReservationStatus.REJECTED){
            reservationRepository.delete(reservation);
        }else {
            reservationRepository.save(reservation);
        }
        AddReservationResponse response = ReservationMapper.INSTANCE.reservationFromResponse(reservation);
        return response;
    }

    @Override
    public void cancelReservation(long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByEmail(username).orElseThrow();
        Reservation reservation = reservationRepository.findById(id).orElseThrow();
        if(reservation.getUser().getId()==user.getId()){
            reservation.setReservationStatus(ReservationStatus.CANCELLED);
            reservation.getRoom().setStatus(Status.AVAILABLE);
            // Kullanıcının rezervasyon iptalinde yöneticiye ve kullanıcıya mail yoluyla bildirim atılması.
            emailService.sendEmailToUser("innvisionmanagement@gmail.com"
                    ,"Rezervasyon İptali","'"+user.getEmail()+"'"+" e-mail'e sahip kullanıcı "+"'"+reservation.getRoom().getRoomNumber()+"'"+" numaralı oda rezervasyonunu iptal etmiştir.");

            emailService.sendEmailToUser(user.getEmail(),"Rezervasyon İptali",+reservation.getRoom().getRoomNumber()+" numaralı oda rezervasyonunuz iptal edilmiştir.");
            reservationRepository.save(reservation);
        }
        else {
            throw new BusinessException("Böyle bir rezervasyonunuz yoktur.");
        }
    }

    @Override
    public DeleteReservationResponse delete(long id) {
        Reservation reservation = reservationRepository.findById(id).orElseThrow(()
                -> new BusinessException("Böyle bir Id'ye sahip rezervasyon bulunamamıştır."));
        DeleteReservationResponse response = ReservationMapper.INSTANCE.reservationFromDeleteResponse(reservation);
        reservationRepository.delete(reservation);
        return response;
    }

    @Override
    public GetResevationResponse getById(long id) {
        Reservation reservation = reservationRepository.findById(id).orElseThrow(()
                -> new BusinessException("Böyle bir Id'ye sahip rezervasyon bulunamadı."));
        GetResevationResponse response = ReservationMapper.INSTANCE.reservationFromGetResponse(reservation);
        return response;
    }

    //Business Rules
    public void isRoomFull(Room room){
        if(room.getStatus()== Status.OCCUPIED){
            throw new BusinessException("Bu oda doludur.");
        }
    }

}
