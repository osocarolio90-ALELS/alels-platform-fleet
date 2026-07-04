package com.alels.backend.telemetry.group.service;

import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.group.dto.TelemetryGroupDtos.DeviceOption;
import com.alels.backend.telemetry.group.dto.TelemetryGroupDtos.GroupRequest;
import com.alels.backend.telemetry.group.dto.TelemetryGroupDtos.GroupRow;
import com.alels.backend.telemetry.group.dto.TelemetryGroupDtos.LogRow;
import com.alels.backend.telemetry.group.repository.TelemetryGroupRepository;

@Service
public class TelemetryGroupService {
    private final TelemetryGroupRepository repository;
    public TelemetryGroupService(TelemetryGroupRepository repository) { this.repository=repository; }
    public List<GroupRow> list(JwtUserContext u){return repository.list(u.companyId(),u.normalizedRole(),false);}
    public List<GroupRow> wasted(JwtUserContext u){return repository.list(u.companyId(),u.normalizedRole(),true);}
    public List<LogRow> logs(JwtUserContext u){return repository.logs(u.companyId(),u.normalizedRole());}
    public List<DeviceOption> devices(JwtUserContext u,Long currentGroupId){
        if(currentGroupId!=null) require(u,currentGroupId,false);
        return repository.devices(u.companyId(),u.normalizedRole(),currentGroupId,true);
    }

    @Transactional public Long create(JwtUserContext u,GroupRequest request){
        Validated v=validate(u,request,null,null);
        Long id=repository.create(v.companyId,v.name,clean(request.description()),u.userId());
        repository.replaceDevices(id,v.ids,u.userId());
        GroupRow group=require(u,id,false); repository.log(group,u.userId(),"CREATE","{}"); return id;
    }
    @Transactional public void update(JwtUserContext u,Long id,GroupRequest request){
        GroupRow current=require(u,id,false); assertEditable(u);
        Validated v=validate(u,request,id,current.companyId());
        repository.update(id,v.name,clean(request.description()),u.userId()); repository.replaceDevices(id,v.ids,u.userId());
        repository.log(require(u,id,false),u.userId(),"UPDATE","{}");
    }
    @Transactional public void delete(JwtUserContext u,Long id){assertEditable(u);GroupRow g=require(u,id,false);repository.log(g,u.userId(),"DELETE","{}");repository.softDelete(id,u.userId());}
    @Transactional public void restore(JwtUserContext u,Long id){assertEditable(u);GroupRow g=require(u,id,true);repository.restore(id,u.userId());repository.log(g,u.userId(),"RESTORE","{}");}
    @Transactional public void permanentDelete(JwtUserContext u,Long id){if(!List.of("SUPERADMIN","OWNER").contains(u.normalizedRole()))throw forbidden();GroupRow g=require(u,id,true);repository.log(g,u.userId(),"PERMANENT_DELETE","{}");repository.permanentDelete(id);}
    private Validated validate(JwtUserContext u,GroupRequest r,Long exclude,Long fixedCompany){
        assertEditable(u); if(r==null||r.groupName()==null||r.groupName().isBlank())throw bad("Group Name wajib diisi.");
        String name=r.groupName().trim().toUpperCase(); List<Long> ids=List.copyOf(new LinkedHashSet<>(r.deviceIds()==null?List.of():r.deviceIds()));
        if(ids.isEmpty()&&fixedCompany==null)throw bad("Pilih minimal satu device untuk menentukan Group Company.");
        List<DeviceOption> visible=repository.devices(u.companyId(),u.normalizedRole(),exclude,false);
        List<DeviceOption> chosen=ids.stream().map(id->visible.stream().filter(d->d.id().equals(id)).findFirst().orElseThrow(()->bad("Device tidak ditemukan, tidak aktif, atau di luar scope."))).toList();
        Long company=fixedCompany!=null?fixedCompany:chosen.get(0).companyId();
        if(chosen.stream().anyMatch(d->!company.equals(d.companyId())))throw bad("Devices inside one Group must belong to the same company.");
        repository.lockDevices(ids);
        for(Long deviceId:ids) repository.activeAssignment(deviceId,exclude).ifPresent(assignment->{
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Device IMEI "+assignment.imei()+" already belongs to Group "+assignment.groupName()+". Remove it from that group first.");
        });
        if(repository.duplicate(company,name,exclude))throw new ResponseStatusException(HttpStatus.CONFLICT,"Group Name sudah digunakan di company ini.");
        return new Validated(company,name,ids);
    }
    private GroupRow require(JwtUserContext u,Long id,boolean wasted){return repository.find(id,u.companyId(),u.normalizedRole(),wasted).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Telemetry Group tidak ditemukan."));}
    private void assertEditable(JwtUserContext u){if("TECHUSER".equals(u.normalizedRole()))throw forbidden();}
    private ResponseStatusException forbidden(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Role hanya memiliki akses view.");}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private String clean(String s){return s==null?null:s.trim();}
    private record Validated(Long companyId,String name,List<Long> ids){}
}
