<template>
  <a-modal v-model="visible" title="设备详情" @ok="handleOk" @cancel="handleClose" width="650px">
    <a-form :form="form" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-item label="设备名称">
        <a-input v-model="form.deviceName"/>
      </a-form-item>
      
      <a-form-item label="绑定角色">
        <a-select v-model="form.roleId">
          <a-select-option v-for="i in roleItems" :key="i.roleId" :value="i.roleId">{{ i.roleName }}</a-select-option>
        </a-select>
      </a-form-item>
      
      <a-form-item label="对话模型">
        <a-cascader 
          style="width: 100%"
          :options="modelOptions" 
          :value="getCascaderValue()"
          @change="handleModelChange" 
          placeholder="请选择模型"
          expandTrigger="hover"
          :allowClear="false" />
      </a-form-item>
      
      <a-form-item label="语音识别">
        <a-select v-model="form.sttId">
          <a-select-option v-for="i in sttItems" :key="i.sttId" :value="i.sttId">{{ i.sttName }}</a-select-option>
        </a-select>
      </a-form-item>
      
      <!-- VAD配置区域 -->
      <a-divider>
        <span>VAD语音检测配置</span>
        <a-tooltip placement="right">
          <template slot="title">VAD (Voice Activity Detection) 用于检测语音的开始和结束</template>
          <a-icon type="question-circle" style="margin-left: 8px" />
        </a-tooltip>
      </a-divider>
      
      <a-form-item 
        label="语音阈值" 
        extra="检测到语音的概率阈值 (0-1)，值越高要求越严格，建议值: 0.5-0.9">
        <a-input-number 
          v-model="form.vadSpeechTh" 
          :min="0" 
          :max="1" 
          :step="0.05" 
          style="width: 100%"
          placeholder="默认0.5" />
      </a-form-item>
      
      <a-form-item 
        label="静音阈值" 
        extra="检测到静音的概率阈值 (0-1)，通常比语音阈值低0.2-0.3">
        <a-input-number 
          v-model="form.vadSilenceTh" 
          :min="0" 
          :max="1" 
          :step="0.05" 
          style="width: 100%"
          placeholder="默认0.3" />
      </a-form-item>
      
      <a-form-item 
        label="能量阈值" 
        extra="音频能量最小阈值 (0-0.5)，用于过滤低能量噪音，嘈杂环境下建议调高">
        <a-input-number 
          v-model="form.vadEnergyTh" 
          :min="0" 
          :max="0.5" 
          :step="0.01" 
          style="width: 100%"
          placeholder="默认0.01" />
      </a-form-item>
      
      <a-form-item 
        label="静音时长" 
        extra="结束语音的静音持续时间 (毫秒)，值越小响应越快，但可能导致过早截断语音">
        <a-input-number 
          v-model="form.vadMinSilenceMs" 
          :min="100" 
          :max="2000" 
          :step="100" 
          style="width: 100%"
          placeholder="默认1200" />
      </a-form-item>
      
      <a-form-item :wrapper-col="{ span: 16, offset: 6 }">
        <a-button type="link" @click="resetVadSettings">
          <a-icon type="redo" />恢复默认VAD设置
        </a-button>
      </a-form-item>
    </a-form>
    
    <template slot="footer">
      <a-popconfirm
        title="确定要清除该设备的所有对话记忆吗？此操作不可恢复。"
        ok-text="确定"
        cancel-text="取消"
        @confirm="handleClearMemory"
      >
        <a-button key="clear" type="danger" :loading="clearMemoryLoading">
          清除记忆
        </a-button>
      </a-popconfirm>
      <a-button key="back" @click="handleClose">
        取消
      </a-button>
      <a-button key="submit" type="primary" @click="handleOk">
        确定
      </a-button>
    </template>
  </a-modal>
</template>

<script>
export default {
  name: "DeviceEditDialog",
  props:{
    visible: Boolean,
    modelItems: Array,
    sttItems: Array,
    roleItems: Array,
    current: Object,
    agentItems: Array,
    clearMemoryLoading: Boolean,
    defaultVadSettings: Object
  },
  data(){
    return {
      form: this.$form.createForm(this, {
        deviceId: "",
        deviceName: "",
        modelId: null,
        modelType: "",
        sttId: null,
        roleId: null,
        provider: "",
        // VAD配置参数
        vadSpeechTh: null,
        vadSilenceTh: null,
        vadEnergyTh: null,
        vadMinSilenceMs: null
      }),
      modelOptions: [
        {
          value: "llm",
          label: "LLM模型",
          children: []
        },
        {
          value: "agent",
          label: "智能体",
          children: [
            {
              value: "coze",
              label: "Coze",
              children: []
            },
            {
              value: "dify",
              label: "Dify",
              children: []
            }
          ]
        }
      ],
      providerMap: {} // 用于存储按提供商分组的模型
    }
  },
  methods:{
    handleClose(){
      this.$emit("close");
    },
    
    handleOk(){
      this.$emit("submit", this.form)
    },
    
    handleClearMemory() {
      this.$emit("clear-memory", this.form);
    },
    
    // 确定模型类型和提供商
    determineModelTypeAndProvider() {
      if (!this.form.modelId) return;
      
      const modelId = Number(this.form.modelId);
      
      // 检查是否为智能体
      const cozeAgent = this.agentItems.find(a => a.provider === 'coze' && Number(a.configId) === modelId);
      if (cozeAgent) {
        this.form.modelType = 'agent';
        this.form.provider = 'coze';
        return;
      }
      
      const difyAgent = this.agentItems.find(a => a.provider === 'dify' && Number(a.configId) === modelId);
      if (difyAgent) {
        this.form.modelType = 'agent';
        this.form.provider = 'dify';
        return;
      }
      
      // 检查是否为LLM模型
      const model = this.modelItems.find(m => Number(m.configId) === modelId);
      if (model) {
        this.form.modelType = 'llm';
        this.form.provider = model.provider || 'other';
      }
    },
    
    // 应用设备组的VAD配置
    applyGroupVadSettings(group) {
      // 检查组是否有VAD设置
      if (group.vadSpeechTh !== undefined && group.vadSpeechTh !== null) {
        this.form.vadSpeechTh = group.vadSpeechTh;
      }
      
      if (group.vadSilenceTh !== undefined && group.vadSilenceTh !== null) {
        this.form.vadSilenceTh = group.vadSilenceTh;
      }
      
      if (group.vadEnergyTh !== undefined && group.vadEnergyTh !== null) {
        this.form.vadEnergyTh = group.vadEnergyTh;
      }
      
      if (group.vadMinSilenceMs !== undefined && group.vadMinSilenceMs !== null) {
        this.form.vadMinSilenceMs = group.vadMinSilenceMs;
      }
    },
    
    // 重置VAD设置为默认值
    resetVadSettings() {
      this.form.vadSpeechTh = this.defaultVadSettings.vadSpeechTh;
      this.form.vadSilenceTh = this.defaultVadSettings.vadSilenceTh;
      this.form.vadEnergyTh = this.defaultVadSettings.vadEnergyTh;
      this.form.vadMinSilenceMs = this.defaultVadSettings.vadMinSilenceMs;
      
      this.$message.success('已恢复默认VAD设置');
    },
    
    // 处理级联选择器变更
    handleModelChange(value) {
      if (!value || value.length < 3) return;
      
      const modelType = value[0]; // llm 或 agent
      const provider = value[1];  // 提供商
      const modelId = Number(value[2]); // 模型ID
      
      this.form.modelId = modelId;
      this.form.modelType = modelType;
      this.form.provider = provider;
      
      // 根据类型设置显示名称和描述
      if (modelType === "llm") {
        const model = this.modelItems.find(item => Number(item.configId) === modelId);
        if (model) {
          this.form.modelName = model.configName || model.modelName;
          this.form.modelDesc = model.configDesc || model.modelDesc;
        }
      } else if (modelType === "agent") {
        // 根据提供商选择智能体列表
        const agentList = this.agentItems.filter(a => a.provider === provider);
        const agent = agentList.find(item => Number(item.configId) === modelId);
        if (agent) {
          this.form.modelName = agent.agentName;
          this.form.modelDesc = agent.agentDesc || '';
        }
      }
    },
    
    // 获取级联选择器的值
    getCascaderValue() {
      if (!this.form.modelId) return [];
      
      // 如果已知模型类型和提供商，直接返回
      if (this.form.modelType === 'agent') {
        if (this.form.provider) {
          return ["agent", this.form.provider, this.form.modelId];
        }
      } else if (this.form.modelType === 'llm' && this.form.provider) {
        return ["llm", this.form.provider, this.form.modelId];
      }
      
      // 如果没有明确的类型和提供商，尝试查找
      const modelId = Number(this.form.modelId);
      
      // 检查是否为智能体
      const cozeAgent = this.agentItems.find(a => a.provider === 'coze' && Number(a.configId) === modelId);
      if (cozeAgent) {
        return ["agent", "coze", modelId];
      }
      
      const difyAgent = this.agentItems.find(a => a.provider === 'dify' && Number(a.configId) === modelId);
      if (difyAgent) {
        return ["agent", "dify", modelId];
      }
      
      // 检查是否为LLM模型
      const model = this.modelItems.find(m => Number(m.configId) === modelId);
      if (model) {
        return ["llm", model.provider || "other", modelId];
      }
      
      // 如果找不到，返回空数组
      return [];
    },
    
    // 更新模型选项
    updateModelOptions() {
      // 重置现有选项
      this.modelOptions[0].children = [];
      this.modelOptions[1].children[0].children = [];
      this.modelOptions[1].children[1].children = [];
      this.providerMap = {};
      
      // 按提供商分组LLM模型
      if (this.modelItems && this.modelItems.length > 0) {
        this.modelItems.forEach(item => {
          const provider = item.provider || "other";
          if (!this.providerMap[provider]) {
            this.providerMap[provider] = [];
          }
          this.providerMap[provider].push(item);
        });
      }
      
      // 添加LLM模型提供商
      for (const provider in this.providerMap) {
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
      }
      
      // 添加智能体选项
      if (this.agentItems && this.agentItems.length > 0) {
        this.agentItems.forEach(item => {
          if (item.provider === 'coze') {
            this.modelOptions[1].children[0].children.push({
              value: item.configId,
              label: item.agentName,
              isLeaf: true,
              data: item
            });
          } else if (item.provider === 'dify') {
            this.modelOptions[1].children[1].children.push({
              value: item.configId,
              label: item.agentName,
              isLeaf: true,
              data: item
            });
          }
        });
      }
    },
    
    // 格式化提供商名称
    formatProviderName(provider) {
      return provider.charAt(0).toUpperCase() + provider.slice(1);
    }
  },
  watch:{
    visible(val){
      if(val){
        // 复制当前设备数据到表单
        this.form = Object.assign({}, this.$props.current);
        
        // 确保VAD设置有默认值
        if (this.form.vadSpeechTh === undefined || this.form.vadSpeechTh === null) {
          this.form.vadSpeechTh = this.defaultVadSettings.vadSpeechTh;
        }
        
        if (this.form.vadSilenceTh === undefined || this.form.vadSilenceTh === null) {
          this.form.vadSilenceTh = this.defaultVadSettings.vadSilenceTh;
        }
        
        if (this.form.vadEnergyTh === undefined || this.form.vadEnergyTh === null) {
          this.form.vadEnergyTh = this.defaultVadSettings.vadEnergyTh;
        }
        
        if (this.form.vadMinSilenceMs === undefined || this.form.vadMinSilenceMs === null) {
          this.form.vadMinSilenceMs = this.defaultVadSettings.vadMinSilenceMs;
        }
        
        // 更新模型选项
        this.updateModelOptions();
      }
    },
    modelItems() {
      this.updateModelOptions();
    },
    agentItems() {
      this.updateModelOptions();
    }
  }
}
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

/* 确保输入框内容居中 */
>>> .ant-input {
  text-align: center !important;
}

/* 确保级联选择器内容居中 */
>>> .ant-cascader-picker-label {
  text-align: center !important;
  width: 100% !important;
}
</style>