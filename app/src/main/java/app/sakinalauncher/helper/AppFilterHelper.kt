package app.sakinalauncher.helper

import app.sakinalauncher.data.AppModel

interface AppFilterHelper {
    fun onAppFiltered(items:List<AppModel>)
}