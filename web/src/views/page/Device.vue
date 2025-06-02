<template>
  <a-layout>
    <a-layout-content>
      <div class="layout-content-margin">
        <!-- 查询框 -->
        <div class="table-search">
          <a-form layout="horizontal" :colon="false" :labelCol="{ span: 6 }" :wrapperCol="{ span: 16 }">
            <a-row class="filter-flex">
              <a-col :xl="6" :lg="12" :xs="24" v-for="(item, index) in queryFilter" :key="index">
                <a-form-item :label="item.label">
                  <a-input-search v-model="item.value" :placeholder="`请输入${item.label}`" allowClear @search="getData()" />
                </a-form-item>
              </a-col>
              <a-col :xxl="6" :xl="6" :lg="12" :xs="24">
                <a-form-item label="设备状态">
                  <a-select v-model="query.state" placeholder="请选择" @change="getData()">
                    <a-select-option v-for="item in stateItems" :key="item.key" :value="item.value">
                      {{ item.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>
        
        <!-- 表格数据 -->
        <a-card :bodyStyle="{ padding: 0 }" :bordered="false">
          <div slot="extra" style="display: flex; align-items: center;">
            <a-input-search enter-button="添加设备" autoFocus placeholder="请输入设备码" @search="addDevice" />
          </div>
          
          <template slot="title">
            <span>设备管理</span>
          </template>
          
          <a-table 
            rowKey="deviceId" 
            :columns="tableColumns" 
            :data-source="data" 
            :loading="loading"
            :pagination="pagination" 
            :scroll="{ x: 1200 }" 
            size="middle">
            
            <!-- 设备名称列 -->
            <template slot="deviceName" slot-scope="text, record">
              <div>
                <a-input v-if="record.editable" style="margin: -5px 0; text-align: center" :value="text" 
                  @change="e => inputEdit(e.target.value, record.deviceId, 'deviceName')" 
                  @keyup.enter="e => update(record, record.deviceId)" 
                  @keyup.esc="e => cancel(record.deviceId)" />
                <span v-else-if="editingKey === ''" @click="edit(record.deviceId)" style="cursor: pointer">
                  <a-tooltip title="点击编辑" :mouseEnterDelay="0.5">
                    <span v-if="text">{{ text }}</span>
                    <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
                  </a-tooltip>
                </span>
                <span v-else>{{ text }}</span>
              </div>
            </template>

            <!-- 角色列 -->
            <template slot="roleName" slot-scope="text, record">
              <a-select v-if="record.editable" style="margin: -5px 0; text-align: center; width: 100%"
                :value="record.roleId" @change="value => handleSelectChange(value, record.deviceId, 'role')">
                <a-select-option v-for="item in roleItems" :key="item.roleId" :value="item.roleId">
                  <div style="text-align: center">{{ item.roleName }}</div>
                </a-select-option>
              </a-select>
              <span v-else-if="editingKey === ''" @click="edit(record.deviceId)" style="cursor: pointer">
                <a-tooltip :title="record.roleDesc" :mouseEnterDelay="0.5" placement="right">
                  <span v-if="record.roleId">{{ getRoleName(record.roleId) }}</span>
                  <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
                </a-tooltip>
              </span>
              <span v-else>{{ text }}</span>
            </template>

            <!-- 模型列 -->
            <template slot="modelName" slot-scope="text, record">
              <a-cascader v-if="record.editable" style="margin: -5px 0; text-align: center; width: 100%"
                :options="modelOptions" :value="getCascaderValue(record)"
                @change="value => handleModelChange(value, record.deviceId)" placeholder="请选择模型"
                expandTrigger="hover" />
              <span v-else-if="editingKey === ''" @click="edit(record.deviceId)" style="cursor: pointer">
                <a-tooltip :title="record.modelDesc || ''" :mouseEnterDelay="0.5">
                  <span v-if="record.modelId && record.modelName">
                    {{ record.modelName }}
                    <a-tag v-if="record.modelType === 'agent'" color="blue" size="small">智能体</a-tag>
                    <a-tag v-if="record.provider" color="green" size="small">{{ record.provider }}</a-tag>
                  </span>
                  <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
                </a-tooltip>
              </span>
              <span v-else>
                {{ record.modelName || '' }}
                <a-tag v-if="record.modelType === 'agent'" color="blue" size="small">智能体</a-tag>
                <a-tag v-if="record.provider" color="green" size="small">{{ record.provider }}</a-tag>
              </span>
            </template>

            <!-- 语音识别列 -->
            <template slot="sttName" slot-scope="text, record">
              <a-select v-if="record.editable" style="margin: -5px 0; text-align: center; width: 100%"
                :value="record.sttId" @change="value => handleSelectChange(value, record.deviceId, 'stt')">
                <a-select-option v-for="item in sttItems" :key="item.sttId" :value="item.sttId">
                  <div style="text-align: center">{{ item.sttName }}</div>
                </a-select-option>
              </a-select>
              <span v-else-if="editingKey === ''" @click="edit(record.deviceId)" style="cursor: pointer">
                <a-tooltip :title="record.sttDesc" :mouseEnterDelay="0.5">
                  <span v-if="record.sttId">{{ getItemName(sttItems, "sttId", record.sttId, "sttName") }}</span>
                  <span v-else style="padding: 0 50px">Vosk本地识别</span>
                </a-tooltip>
              </span>
              <span v-else>{{ getItemName(sttItems, "sttId", record.sttId, "sttName") }}</span>
            </template>

            <!-- 设备状态列 -->
            <template slot="state" slot-scope="text">
              <a-tag :color="text == 1 ? 'green' : 'red'">{{ text == 1 ? '在线' : '离线' }}</a-tag>
            </template>

            <!-- 时间列通用模板 -->
            <template slot="timeColumn" slot-scope="text">
              {{ text || '-' }}
            </template>

            <!-- 操作列 -->
            <template slot="operation" slot-scope="text, record">
              <a-space v-if="record.editable">
                <a-popconfirm title="确定保存？" @confirm="update(record, record.deviceId)">
                  <a>保存</a>
                </a-popconfirm>
                <a @click="cancel(record.deviceId)">取消</a>
              </a-space>
              <a-space v-else>
                <a @click="edit(record.deviceId)">编辑</a>
                <a @click="editWithDialog(record)">详情</a>
                <a-popconfirm
                  title="确定要删除此设备吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="deleteDevice(record)"
                >
                  <a style="color: #ff4d4f">删除</a>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table>
        </a-card>
      </div>
    </a-layout-content>
    
    <!-- 设备详情弹窗 -->
    <DeviceEditDialog 
      @submit="update" 
      @close="editVisible = false" 
      @clear-memory="clearMemory"
      :visible="editVisible" 
      :current="currentDevice"
      :model-items="modelItems" 
      :stt-items="sttItems" 
      :role-items="roleItems"
      :agent-items="agentItems"
      :default-vad-settings="defaultVadSettings"
      :clearMemoryLoading="clearMemoryLoading"/>

    <a-back-top />
  </a-layout>
</template>
<script>
import axios from "@/services/axios";
import api from "@/services/api";
import mixin from "@/mixins/index";
import DeviceEditDialog from "@/components/DeviceEditDialog.vue";

// 模型类型常量
const MODEL_TYPE = {
  LLM: 'llm',
  AGENT: 'agent',
  UNKNOWN: 'unknown'
};

// 提供商常量
const PROVIDER = {
  COZE: 'coze',
  DIFY: 'dify',
  OTHER: 'other'
};

export default {
  components: { DeviceEditDialog },
  mixins: [mixin],
  data() {
    return {
      // 查询框
      editVisible: false,
      currentDevice: {},
      query: {
        state: "",
      },
      queryFilter: [
        {
          label: "设备编号",
          value: "",
          index: "deviceId",
        },
        {
          label: "设备名称",
          value: "",
          index: "deviceName",
        },
      ],
      stateItems: [
        { label: "全部", value: "", key: "" },
        { label: "在线", value: "1", key: "1" },
        { label: "离线", value: "0", key: "0" },
      ],
      
      // 表格数据
      tableColumns: [
        {
          title: "设备编号",
          dataIndex: "deviceId",
          width: 160,
          fixed: "left",
          align: "center",
        },
        {
          title: "设备名称",
          dataIndex: "deviceName",
          scopedSlots: { customRender: "deviceName" },
          width: 100,
          align: "center",
        },
        {
          title: "设备角色",
          dataIndex: "roleName",
          scopedSlots: { customRender: "roleName" },
          width: 100,
          align: "center",
        },
        {
          title: "模型",
          dataIndex: "modelName",
          scopedSlots: { customRender: "modelName" },
          width: 150,
          align: "center",
        },
        {
          title: "语音识别",
          dataIndex: "sttName",
          scopedSlots: { customRender: "sttName" },
          width: 150,
          align: "center",
        },
        {
          title: "WIFI名称",
          dataIndex: "wifiName",
          width: 100,
          align: "center",
          ellipsis: true,
        },
        {
          title: "IP地址",
          dataIndex: "ip",
          width: 180,
          align: "center",
          ellipsis: true,
        },
        {
          title: "设备状态",
          dataIndex: "state",
          scopedSlots: { customRender: "state" },
          width: 100,
          align: "center",
        },
        {
          title: "产品类型",
          dataIndex: "chipModelName",
          width: 100,
          align: "center",
        },
        {
          title: "设备类型",
          dataIndex: "type",
          width: 150,
          align: "center",
          ellipsis: true,
        },
        {
          title: "版本号",
          dataIndex: "version",
          width: 100,
          align: "center",
        },
        {
          title: "活跃时间",
          dataIndex: "lastLogin",
          scopedSlots: { customRender: "timeColumn" },
          width: 180,
          align: "center",
        },
        {
          title: "创建时间",
          dataIndex: "createTime",
          scopedSlots: { customRender: "timeColumn" },
          width: 180,
          align: "center",
        },
        {
          title: "操作",
          dataIndex: "operation",
          scopedSlots: { customRender: "operation" },
          width: 150,
          align: "center",
          fixed: "right",
        },
      ],
      
      // 资源数据
      roleItems: [],
      modelItems: [],
      agentItems: [],
      cozeAgents: [],
      difyAgents: [],
      sttItems: [],
      ttsItems: [],
      
      // 三级级联选择器结构
      modelOptions: [
        {
          value: MODEL_TYPE.LLM,
          label: "LLM模型",
          children: []
        },
        {
          value: MODEL_TYPE.AGENT,
          label: "智能体",
          children: [
            {
              value: PROVIDER.COZE,
              label: "Coze",
              children: []
            },
            {
              value: PROVIDER.DIFY,
              label: "Dify",
              children: []
            }
          ]
        }
      ],
      providerMap: {}, // 用于存储按提供商分组的模型
      
      // 设备数据
      data: [],
      cacheData: [],
      editingKey: "",
      
      // 加载状态标志
      clearMemoryLoading: false,
      
      // VAD默认设置
      defaultVadSettings: {
        vadSpeechTh: 0.5,
        vadSilenceTh: 0.3,
        vadEnergyTh: 0.01,
        vadMinSilenceMs: 1200
      },
    };
  },
  
  mounted() {
    // 并行加载所有基础数据
    Promise.all([
      this.getRole(),
      this.getConfig(),
      this.getAgents()
    ]).then(() => {
      // 基础数据加载完成后，加载设备数据
      this.getData();
    });
  },
  
  methods: {
    /**
     * 数据获取方法
     */
    // 获取设备列表数据
    getData() {
      this.loading = true;
      this.editingKey = "";
      
      // 构建查询参数
      const queryParams = {
        start: this.pagination.page,
        limit: this.pagination.pageSize,
        ...this.query,
      };
      
      // 添加过滤条件
      this.queryFilter.forEach(filter => {
        if (filter.value) {
          queryParams[filter.index] = filter.value;
        }
      });
      
      axios
        .get({
          url: api.device.query,
          data: queryParams,
        })
        .then((res) => {
          if (res.code === 200) {
            // 处理设备数据
            const deviceList = res.data.list.map((item) => {
              // 确保sttId有值
              item.sttId = item.sttId || -1;
              // 处理模型类型
              this.determineModelType(item);
              return item;
            });

            this.data = deviceList;
            this.cacheData = deviceList.map((item) => ({ ...item }));
            this.pagination.total = res.data.total;
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.loading = false;
        });
    },
    
    // 获取角色列表
    getRole() {
      return axios
        .get({ url: api.role.query, data: {} })
        .then((res) => {
          if (res.code === 200) {
            this.roleItems = res.data.list;
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        });
    },
    
    // 获取模型配置列表
    getConfig() {
      return axios
        .get({ url: api.config.query })
        .then((res) => {
          if (res.code === 200) {
            // 添加默认的本地语音识别
            this.sttItems.push({
              sttId: -1,
              sttName: "Vosk本地识别",
              sttDesc: "默认Vosk本地语音识别模型",
            });

            // 初始化提供商映射
            this.providerMap = {};

            // 处理配置数据
            res.data.list.forEach((item) => {
              if (item.configType === "llm") {
                this.processLlmModel(item);
              } else if (item.configType === "stt") {
                this.processSttModel(item);
              }
            });

            // 重建模型选项结构
            this.rebuildModelOptions();
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        });
    },
    
    // 处理LLM模型数据
    processLlmModel(item) {
      // 标准化字段
      item.modelId = item.configId;
      item.modelName = item.configName;
      item.modelDesc = item.configDesc;
      this.modelItems.push(item);

      // 按提供商分组
      const provider = item.provider || PROVIDER.OTHER;
      if (!this.providerMap[provider]) {
        this.providerMap[provider] = [];
      }
      this.providerMap[provider].push(item);
    },
    
    // 处理STT模型数据
    processSttModel(item) {
      item.sttId = item.configId;
      item.sttName = item.configName;
      item.sttDesc = item.configDesc;
      this.sttItems.push(item);
    },
    
    // 获取智能体列表
    getAgents() {
      // 清空现有智能体列表
      this.agentItems = [];
      this.cozeAgents = [];
      this.difyAgents = [];
      
      // 并行请求两个提供商的智能体
      return Promise.all([
        this.getProviderAgents(PROVIDER.COZE),
        this.getProviderAgents(PROVIDER.DIFY)
      ]);
    },
    
    // 获取指定提供商的智能体
    getProviderAgents(provider) {
      return axios
        .get({
          url: api.agent.query,
          data: { provider },
        })
        .then((res) => {
          if (res.code === 200) {
            // 处理智能体数据
            res.data.list.forEach((item) => {
              this.processAgentItem(item, provider);
            });
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        });
    },
    
    // 处理智能体数据
    processAgentItem(item, provider) {
      // 标准化字段
      item.modelId = item.configId;
      item.provider = provider;
      
      // 添加到对应提供商的列表
      if (provider === PROVIDER.COZE) {
        this.cozeAgents.push(item);
      } else if (provider === PROVIDER.DIFY) {
        this.difyAgents.push(item);
      }
      
      // 添加到总智能体列表
      this.agentItems.push(item);
      
      // 添加到级联选择器选项
      const providerOption = this.modelOptions[1].children.find(p => p.value === provider);
      if (providerOption) {
        providerOption.children.push({
          value: item.configId,
          label: item.agentName,
          isLeaf: true,
          data: item
        });
      }
    },
    
    /**
     * 模型选项和类型处理方法
     */
    // 重建模型选项结构
    rebuildModelOptions() {
      // 清空现有LLM模型提供商
      this.modelOptions[0].children = [];

      // 添加LLM模型提供商
      Object.keys(this.providerMap).forEach(provider => {
        const models = this.providerMap[provider];
        const providerOption = {
          value: provider,
          label: this.formatProviderName(provider),
          children: []
        };

        // 添加该提供商下的所有模型
        models.forEach(model => {
          providerOption.children.push({
            value: model.configId,
            label: model.configName,
            isLeaf: true,
            data: model
          });
        });

        // 将提供商选项添加到LLM类别下
        this.modelOptions[0].children.push(providerOption);
      });
    },
    
    // 格式化提供商名称
    formatProviderName(provider) {
      return provider.charAt(0).toUpperCase() + provider.slice(1);
    },
    
    // 确定模型类型（LLM或智能体）
    determineModelType(device) {
      if (!device.modelId) {
        // 确保没有modelId的设备也有基本属性
        this.resetDeviceModelInfo(device);
        return;
      }

      // 转换为数字进行比较（确保类型一致）
      const modelId = Number(device.modelId);

      // 按优先级检查模型类型
      if (this.findAndApplyAgentModel(device, modelId, this.cozeAgents, PROVIDER.COZE)) return;
      if (this.findAndApplyAgentModel(device, modelId, this.difyAgents, PROVIDER.DIFY)) return;
      if (this.findAndApplyLlmModel(device, modelId)) return;
      
      // 未找到匹配的模型，设置为未知
      console.warn(`未找到ID为${modelId}的模型或智能体`);
      device.modelType = MODEL_TYPE.UNKNOWN;
      device.modelName = `未知模型(ID:${modelId})`;
      device.modelDesc = '';
      device.provider = '';
    },
    
    // 重置设备模型信息
    resetDeviceModelInfo(device) {
      device.modelType = '';
      device.modelName = '';
      device.modelDesc = '';
      device.provider = '';
    },
    
    // 查找并应用智能体模型
    findAndApplyAgentModel(device, modelId, agentList, provider) {
      const agent = agentList.find(a => Number(a.configId) === modelId);
      if (agent) {
        device.modelType = MODEL_TYPE.AGENT;
        device.modelName = agent.agentName || '未知智能体';
        device.modelDesc = agent.agentDesc || '';
        device.provider = provider;
        return true;
      }
      return false;
    },
    
    // 查找并应用LLM模型
    findAndApplyLlmModel(device, modelId) {
      const model = this.modelItems.find(m => Number(m.configId) === modelId);
      if (model) {
        device.modelType = MODEL_TYPE.LLM;
        device.modelName = model.configName || '未知模型';
        device.modelDesc = model.configDesc || '';
        device.provider = model.provider || '';
        return true;
      }
      return false;
    },
    
    // 获取级联选择器的值
    getCascaderValue(record) {
      if (!record.modelId) return [];

      // 转换为数字类型，确保类型一致性
      const modelId = Number(record.modelId);

      // 如果已知模型类型和提供商，直接返回
      if (record.modelType === MODEL_TYPE.AGENT) {
        if (record.provider === PROVIDER.COZE) {
          return [MODEL_TYPE.AGENT, PROVIDER.COZE, modelId];
        } else if (record.provider === PROVIDER.DIFY) {
          return [MODEL_TYPE.AGENT, PROVIDER.DIFY, modelId];
        }
      } else if (record.modelType === MODEL_TYPE.LLM && record.provider) {
        return [MODEL_TYPE.LLM, record.provider, modelId];
      }

      // 如果没有明确的类型和提供商，尝试查找
      // 按优先级查找
      if (this.cozeAgents.some(a => Number(a.configId) === modelId)) {
        return [MODEL_TYPE.AGENT, PROVIDER.COZE, modelId];
      }
      
      if (this.difyAgents.some(a => Number(a.configId) === modelId)) {
        return [MODEL_TYPE.AGENT, PROVIDER.DIFY, modelId];
      }
      
      const model = this.modelItems.find(m => Number(m.configId) === modelId);
      if (model && model.provider) {
        return [MODEL_TYPE.LLM, model.provider, modelId];
      }
      
      // 如果找不到提供商，返回空数组
      return [];
    },
    
    // 将模型ID应用到设备
    applyModelToDevice(modelId, device) {
      // 转换为数字进行比较
      const numModelId = Number(modelId);
      
      // 按优先级检查模型类型
      if (this.findAndApplyAgentModel(device, numModelId, this.cozeAgents, PROVIDER.COZE)) return;
      if (this.findAndApplyAgentModel(device, numModelId, this.difyAgents, PROVIDER.DIFY)) return;
      if (this.findAndApplyLlmModel(device, numModelId)) return;
      
      // 未找到匹配的模型，保持原样
      console.warn(`未找到ID为${numModelId}的模型或智能体`);
    },
    
    /**
     * 设备操作方法
     */
    // 添加设备
    addDevice(value) {
      if (!value) {
        this.$message.info("请输入设备编号");
        return;
      }
      
      axios
        .post({
          url: api.device.add,
          data: {
            code: value
          },
        })
        .then((res) => {
          if (res.code === 200) {
            this.$message.success("设备添加成功");
            this.getData();
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        });
    },
    
    // 删除设备
    deleteDevice(record) {
      this.loading = true;
      axios
        .post({
          url: api.device.delete,
          data: { deviceId: record.deviceId }
        })
        .then((res) => {
          if (res.code === 200) {
            this.$message.success("设备删除成功");
            this.getData();
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.loading = false;
        });
    },
    
    // 更新设备信息
    update(val, key) {
      if (key) {
        this.loading = true;
        delete val.editable;
      }

      axios
        .post({
          url: api.device.update,
          data: {
            deviceId: val.deviceId,
            deviceName: val.deviceName,
            modelId: val.modelId,
            sttId: val.sttId,
            ttsId: val.ttsId,
            roleId: val.roleId,
            vadEnergyTh: val.vadEnergyTh,
            vadSpeechTh: val.vadSpeechTh,
            vadSilenceTh: val.vadSilenceTh,
            vadSilenceMs: val.vadSilenceMs
          }
        })
        .then((res) => {
          if (res.code === 200) {
            this.getData();
            this.editVisible = false;
            this.$message.success("修改成功");
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.loading = false;
          this.editingKey = "";
        });
    },
    
    // 清除设备记忆
    clearMemory(record) {
      this.clearMemoryLoading = true;
      axios
        .post({
          url: api.message.delete,
          data: { deviceId: record.deviceId }
        })
        .then((res) => {
          if (res.code === 200) {
            this.editVisible = false;
            this.$message.success("记忆清除成功");
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.clearMemoryLoading = false;
        });
    },
    
    // 在弹窗中编辑设备
    editWithDialog(device) {
      this.editVisible = true;
      this.currentDevice = { ...device };
    },
    
    /**
     * 下拉选择和级联选择处理
     */
    // 选择变更处理函数
    handleSelectChange(value, key, type) {
      // 获取编辑中的数据行
      const data = this.editLine(key);
      
      let items, idField, nameField;

      // 根据类型确定字段和数据源
      if (type === "role") {
        items = this.roleItems;
        idField = "roleId";
        nameField = "roleName";
      } else if (type === "model") {
        items = this.modelItems;
        idField = "modelId";
        nameField = "modelName";
      } else if (type === "stt") {
        items = this.sttItems;
        idField = "sttId";
        nameField = "sttName";
      } else {
        return; // 不支持的类型，直接返回
      }

      // 查找对应的项
      const item = items.find((item) => item[idField] === value);
      const name = item ? item[nameField] : "";

      // 更新数据
      data.target[idField] = value;
      data.target[nameField] = name;
      
      this.data = [...this.data]; // 强制更新视图
    },
    
    // 处理级联选择器变更
    handleModelChange(value, deviceId) {
      if (!value || value.length < 3) return;
     
      const modelType = value[0]; // llm 或 agent
      const provider = value[1];  // 提供商
      const modelId = Number(value[2]); // 模型ID
      
      const data = this.editLine(deviceId);
      data.target.modelId = modelId; // 保存modelId，这是传给后端的值
      data.target.modelType = modelType; // 保存模型类型，仅前端使用
      data.target.provider = provider; // 保存提供商，用于显示和分类

      // 根据类型设置显示名称和描述
      this.updateModelDisplayInfo(data.target, modelType, provider, modelId);
      this.data = [...this.data]; // 强制更新视图
    },
    
    // 更新模型显示信息
    updateModelDisplayInfo(device, modelType, provider, modelId) {
      if (modelType === MODEL_TYPE.LLM) {
        const model = this.modelItems.find(item => Number(item.configId) === modelId);
        if (model) {
          device.modelName = model.configName;
          device.modelDesc = model.configDesc;
        } else {
          device.modelName = `未知模型(ID:${modelId})`;
          device.modelDesc = '';
        }
      } else if (modelType === MODEL_TYPE.AGENT) {
        // 根据提供商选择智能体列表
        const agentList = provider === PROVIDER.COZE ? this.cozeAgents : this.difyAgents;
        
        // 从智能体列表中找到对应的智能体
        const agent = agentList.find(item => Number(item.configId) === modelId);
        if (agent) {
          device.modelName = agent.agentName;
          device.modelDesc = agent.agentDesc;
        } else {
          device.modelName = `未知智能体(ID:${modelId})`;
          device.modelDesc = '';
        }
      }
    },
    
    // 获取角色名称
    getRoleName(roleId) {
      if (!roleId) return "";
      
      const role = this.roleItems.find(r => r.roleId === roleId);
      return role ? role.roleName : `角色ID:${roleId}`;
    },
    
    // 获取项目名称的辅助方法
    getItemName(items, idField, id, nameField) {
      const item = items.find(item => item[idField] === id);
      return item ? item[nameField] : "";
    }
  },
};
</script>

<style scoped>
/* 确保所有下拉框中的文本居中 */
>>> .ant-select-selection__rendered .ant-select-selection-selected-value {
  text-align: center !important;
  width: 100% !important;
}

/* 查询框中的下拉框保持默认对齐方式 */
>>> .table-search .ant-select-selection-selected-value {
  text-align: left !important;
}
</style>