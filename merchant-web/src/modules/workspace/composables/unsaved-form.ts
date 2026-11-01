import { ref, type Ref } from 'vue';
import { ElMessageBox } from 'element-plus';

export function useUnsavedForm(form: Ref<any>, visible: Ref<boolean>, saving: Ref<boolean>) {
	const original = ref('');
	function markClean() {
		original.value = JSON.stringify(form.value);
	}
	async function close(done?: () => void) {
		if (saving.value) return;
		if (JSON.stringify(form.value) !== original.value) {
			try {
				await ElMessageBox.confirm('表单修改尚未保存，确定放弃本次修改？', '未保存的修改', {
					confirmButtonText: '放弃修改',
					cancelButtonText: '继续编辑',
					type: 'warning'
				});
			} catch {
				return;
			}
		}
		visible.value = false;
		done?.();
	}
	return { markClean, close };
}
