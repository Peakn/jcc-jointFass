package com.fc.springcloud.service.Impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.IdUtil;
import com.fc.springcloud.pojo.domain.FunctionFileDo;
import com.fc.springcloud.enums.ResultCode;
import com.fc.springcloud.exception.OutOfBusinessException;
import com.fc.springcloud.service.FileService;
import com.fc.springcloud.pojo.vo.FunctionFileVo;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Field;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Service
public class FileServiceImpl implements FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    private GridFSBucket gridFSBucket;


    /**
     * 保存文件
     *
     * @param file
     * @return
     */
    @Override
    public FunctionFileDo saveFile(FunctionFileDo file) {
        file = mongoTemplate.save(file);
        return file;
    }

    /**
     * 上传文件到Mongodb的GridFs中
     *
     * @param in
     * @param contentType
     * @return
     */
    @Override
    public String uploadFileToGridFS(InputStream in, String contentType) {
        String gridFsId = IdUtil.fastUUID();
        //将文件存储进GridFS中
        gridFsTemplate.store(in, gridFsId, contentType);
        return gridFsId;
    }

    /**
     * 删除文件
     *
     * @param id
     */
    @Override
    public void removeFile(String id) {
        //根据id查询文件
        FunctionFileDo functionFileDo = mongoTemplate.findById(id, FunctionFileDo.class);

        if (functionFileDo == null) {
            logger.error("File is not exist. delete file id:[{}]", id);
            throw new OutOfBusinessException("File is not exist.", ResultCode.OUT_OF_BUSINESS.getCode());
        }
        //根据文件ID删除fs.files和fs.chunks中的记录
        Query deleteFileQuery = new Query().addCriteria(Criteria.where("filename").is(functionFileDo.getGridFsId()));
        gridFsTemplate.delete(deleteFileQuery);
        //删除集合fileDocument中的数据
        Query deleteQuery = new Query(Criteria.where("id").is(id));
        mongoTemplate.remove(deleteQuery, FunctionFileDo.class);

    }

    /**
     * 根据id查看文件
     *
     * @param id
     * @return
     */
    @Override
    public Optional<FunctionFileDo> getFileById(String id) {
        FunctionFileDo functionFileDo = mongoTemplate.findById(id, FunctionFileDo.class);
        if (functionFileDo != null) {
            Query gridQuery = new Query().addCriteria(Criteria.where("filename").is(functionFileDo.getGridFsId()));
            try {
                //根据id查询文件
                GridFSFile fsFile = gridFsTemplate.findOne(gridQuery);
                //打开流下载对象
                GridFSDownloadStream in = gridFSBucket.openDownloadStream(fsFile.getObjectId());
                if (in.getGridFSFile().getLength() > 0) {
                    //获取流对象
                    GridFsResource resource = new GridFsResource(fsFile, in);
                    //获取数据
                    functionFileDo.setContent(IoUtil.readBytes(resource.getInputStream()));
                    return Optional.of(functionFileDo);
                } else {
                    return Optional.empty();
                }
            } catch (IOException ex) {
                logger.error("file is not exist, please check file id:[{}] .  ex:[{}]", id, ex);
            }
        }
        return Optional.empty();
    }


    /**
     * 分页列出文件
     *
     * @param pageIndex
     * @param pageSize
     * @return
     */
    @Override
    public List<FunctionFileDo> listFilesByPage(int pageIndex, int pageSize) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "uploadDate"));
        long skip = (pageIndex - 1) * pageSize;
        query.skip(skip);
        query.limit(pageSize);
        Field field = query.fields();
        field.exclude("content");
        List<FunctionFileDo> files = mongoTemplate.find(query, FunctionFileDo.class);
        return files;
    }

    @Override
    public void updateFileById(FunctionFileVo functionFileVo) {
        Query query = new Query(Criteria.where("id").is(functionFileVo.getId()));
        Update update = new Update().set("functionId", functionFileVo.getFunctionId());
        mongoTemplate.updateFirst(query, update, FunctionFileDo.class);
    }
}